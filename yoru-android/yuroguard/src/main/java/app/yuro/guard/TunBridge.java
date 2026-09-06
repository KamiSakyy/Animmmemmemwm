package app.yuro.guard;

import android.os.ParcelFileDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TunBridge {
    private final GuardVpnService service;
    private final ParcelFileDescriptor fd;
    private final NetDb db;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final ExecutorService io = Executors.newCachedThreadPool();
    private final Map<String, TcpConn> tcp = new HashMap<>();
    private final Object writeLock = new Object();
    private final AtomicInteger ipId = new AtomicInteger(new Random().nextInt(60000));
    private Thread loop;
    private FileOutputStream out;

    public TunBridge(GuardVpnService service, ParcelFileDescriptor fd, NetDb db) {
        this.service = service; this.fd = fd; this.db = db;
    }

    public void start() {
        loop = new Thread(this::run, "yuro-tun-bridge");
        loop.start();
    }

    public void stop() {
        stopped.set(true);
        try { fd.close(); } catch (Throwable ignored) {}
        synchronized (tcp) { for (TcpConn c : tcp.values()) close(c.remote); tcp.clear(); }
        io.shutdownNow();
        if (loop != null) loop.interrupt();
    }

    private void run() {
        byte[] buf = new byte[32767];
        try (FileInputStream in = new FileInputStream(fd.getFileDescriptor()); FileOutputStream fout = new FileOutputStream(fd.getFileDescriptor())) {
            out = fout;
            while (!stopped.get()) {
                int n = in.read(buf);
                if (n < 0) break;
                PacketInspector.inspect(service, buf, n, db);
                handle(buf, n);
            }
        } catch (Throwable ignored) {}
    }

    private void handle(byte[] p, int len) {
        if (len < 20) return;
        int version = (p[0] >> 4) & 0x0f;
        if (version != 4) return;
        int ihl = (p[0] & 0x0f) * 4;
        if (ihl < 20 || len < ihl) return;
        int proto = p[9] & 0xff;
        if (proto == 6) handleTcp(p, len, ihl);
        else if (proto == 17) handleUdp(p, len, ihl);
    }

    private void handleTcp(byte[] p, int len, int ihl) {
        if (len < ihl + 20) return;
        int srcPort = u16(p, ihl), dstPort = u16(p, ihl + 2);
        long seq = u32(p, ihl + 4);
        int dataOff = ((p[ihl + 12] >> 4) & 0x0f) * 4;
        int flags = p[ihl + 13] & 0xff;
        int payload = ihl + dataOff;
        int plen = Math.max(0, len - payload);
        byte[] src = Arrays.copyOfRange(p, 12, 16), dst = Arrays.copyOfRange(p, 16, 20);
        String dstIp = ip(dst), key = ip(src) + ":" + srcPort + "-" + dstIp + ":" + dstPort;
        TcpConn c;
        synchronized (tcp) { c = tcp.get(key); }
        if ((flags & 0x04) != 0) { remove(key); return; }
        if ((flags & 0x02) != 0) {
            c = new TcpConn(); c.key = key; c.clientIp = src; c.remoteIp = dst; c.clientPort = srcPort; c.remotePort = dstPort; c.clientNext = seq + 1; c.serverSeq = new Random().nextInt(1_000_000) + 1000L; c.host = db.hostForIp(dstIp); if (c.host.length()==0) c.host = dstIp;
            synchronized (tcp) { tcp.put(key, c); }
            sendTcp(c, 0x12, c.serverSeq, c.clientNext, null, 0, 0);
            c.serverSeq++;
            connectRemote(c);
            return;
        }
        if (c == null) return;
        if (plen > 0) {
            c.clientNext = seq + plen;
            byte[] data = Arrays.copyOfRange(p, payload, payload + plen);
            sendTcp(c, 0x10, c.serverSeq, c.clientNext, null, 0, 0);
            writeRemote(c, data, plen);
        } else if ((flags & 0x01) != 0) {
            c.clientNext = seq + 1;
            sendTcp(c, 0x11, c.serverSeq, c.clientNext, null, 0, 0);
            remove(c.key);
        }
    }

    private void connectRemote(TcpConn c) {
        io.execute(() -> {
            try {
                Socket s = new Socket();
                service.protect(s);
                s.setTcpNoDelay(true); s.setKeepAlive(true); s.setSendBufferSize(4096); s.setReceiveBufferSize(16384);
                s.connect(new InetSocketAddress(c.host, c.remotePort), 9000);
                c.remote = s; c.connected = true;
                db.recordEvent(-1, "YURO VPN", "TCP", c.host, ip(c.remoteIp), c.remotePort, "vpn bridge", false, "connected · auto DPI level " + GuardPrefs.dpiAutoLevel(service));
                synchronized (c.pending) { for (byte[] b : c.pending) writeRemote(c, b, b.length); c.pending.clear(); }
                readRemote(c);
            } catch (Throwable e) {
                db.recordEvent(-1, "YURO VPN", "TCP", c.host, ip(c.remoteIp), c.remotePort, "vpn bridge fail", true, e.getClass().getSimpleName());
                sendTcp(c, 0x14, c.serverSeq, c.clientNext, null, 0, 0);
                remove(c.key);
            }
        });
    }

    private void writeRemote(TcpConn c, byte[] data, int len) {
        if (!c.connected || c.remote == null) { synchronized (c.pending) { if (c.pending.size() < 16) c.pending.add(Arrays.copyOf(data, len)); } return; }
        try {
            OutputStream os = c.remote.getOutputStream();
            if (!c.firstSent && GuardPrefs.dpiBypass(service)) { c.firstSent = true; sendAdaptive(os, data, len, c.host, c.remotePort); }
            else { os.write(data, 0, len); os.flush(); }
        } catch (Throwable e) { remove(c.key); }
    }

    private void readRemote(TcpConn c) {
        byte[] b = new byte[8192];
        try {
            InputStream is = c.remote.getInputStream();
            while (!stopped.get()) {
                int n = is.read(b); if (n < 0) break;
                sendTcp(c, 0x18, c.serverSeq, c.clientNext, b, 0, n);
                c.serverSeq += n;
            }
            sendTcp(c, 0x11, c.serverSeq, c.clientNext, null, 0, 0);
        } catch (Throwable ignored) {} finally { remove(c.key); }
    }

    private void handleUdp(byte[] p, int len, int ihl) {
        if (len < ihl + 8) return;
        int srcPort = u16(p, ihl), dstPort = u16(p, ihl + 2);
        int payload = ihl + 8, plen = len - payload;
        byte[] src = Arrays.copyOfRange(p, 12, 16), dst = Arrays.copyOfRange(p, 16, 20);
        String dstIp = ip(dst); String host = db.hostForIp(dstIp);
        if (GuardPrefs.dpiBypass(service) && GuardPrefs.dpiQuicBlock(service) && dstPort == 443) {
            db.recordEvent(-1, "YURO VPN", "UDP", host, dstIp, dstPort, "QUIC blocked", true, "force Telegram/HTTP3 fallback to TCP");
            return;
        }
        byte[] data = Arrays.copyOfRange(p, payload, payload + plen);
        io.execute(() -> {
            DatagramSocket ds = null;
            try {
                ds = new DatagramSocket(); service.protect(ds); ds.setSoTimeout(2500);
                ds.send(new DatagramPacket(data, data.length, InetAddress.getByAddress(dst), dstPort));
                byte[] rb = new byte[4096]; DatagramPacket resp = new DatagramPacket(rb, rb.length); ds.receive(resp);
                byte[] outData = Arrays.copyOf(resp.getData(), resp.getLength());
                sendUdp(dst, src, dstPort, srcPort, outData, outData.length);
                db.recordEvent(-1, "YURO VPN", "UDP", host, dstIp, dstPort, dstPort == 53 ? "DNS forward" : "UDP forward", false, resp.getLength() + "b");
            } catch (Throwable e) { db.recordEvent(-1, "YURO VPN", "UDP", host, dstIp, dstPort, "UDP fail", true, e.getClass().getSimpleName()); }
            finally { if (ds != null) ds.close(); }
        });
    }

    private void sendAdaptive(OutputStream os, byte[] data, int len, String host, int port) throws Exception {
        int level = GuardPrefs.dpiAutoLevel(service);
        int sni = findSniOffset(data, len);
        int first = sni > 8 ? Math.max(1, sni - 1) : (level >= 4 ? 1 : level >= 3 ? Math.min(5, len - 1) : Math.min(12, len - 1));
        if (len <= 3 || level <= 0) { os.write(data,0,len); os.flush(); return; }
        first = Math.max(1, Math.min(first, len - 1));
        db.recordEvent(-1, "YURO VPN", "DPI", host, host, port, "TCP split", false, "level " + level + (sni > 0 ? " · SNI " + sni : " · first-flight"));
        if (level >= 4) {
            for (int i=0;i<Math.min(len, 8);i++) { os.write(data, i, 1); os.flush(); sleep(4); }
            if (len > 8) { os.write(data, 8, len - 8); os.flush(); }
        } else {
            os.write(data, 0, first); os.flush(); sleep(level >= 3 ? 35 : 12); os.write(data, first, len - first); os.flush();
        }
    }

    private int findSniOffset(byte[] p, int len) {
        try {
            if (len < 9 || (p[0] & 0xff) != 22) return -1;
            int hs = 5; if ((p[hs] & 0xff) != 1) return -1;
            int pos = hs + 4 + 2 + 32; int sid = p[pos++] & 0xff; pos += sid;
            int cipher = u16(p,pos); pos += 2 + cipher; int comp = p[pos++] & 0xff; pos += comp;
            int extEnd = Math.min(len, pos + 2 + u16(p,pos)); pos += 2;
            while (pos + 4 <= extEnd) { int type = u16(p,pos), elen = u16(p,pos+2); pos += 4; if (type == 0 && pos + 5 <= extEnd) { int npos = pos + 2; int nt = p[npos++] & 0xff; int nl = u16(p,npos); npos += 2; if (nt == 0 && nl > 0 && npos + nl <= len) return npos; } pos += elen; }
        } catch (Throwable ignored) {}
        return -1;
    }

    private void sendTcp(TcpConn c, int flags, long seq, long ack, byte[] data, int off, int len) {
        try { sendIpTcp(c.remoteIp, c.clientIp, c.remotePort, c.clientPort, seq, ack, flags, data, off, len); } catch (Throwable ignored) {}
    }

    private void sendIpTcp(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, long seq, long ack, int flags, byte[] data, int off, int len) throws Exception {
        int total = 20 + 20 + len; byte[] p = new byte[total];
        buildIp(p, total, 6, srcIp, dstIp);
        int t = 20; put16(p,t,srcPort); put16(p,t+2,dstPort); put32(p,t+4,seq); put32(p,t+8,ack); p[t+12]=(byte)(5<<4); p[t+13]=(byte)flags; put16(p,t+14,65535); if (len>0) System.arraycopy(data,off,p,t+20,len);
        put16(p,t+16,0); int sum = tcpUdpChecksum(p, srcIp, dstIp, 6, t, 20+len); put16(p,t+16,sum);
        writePacket(p);
    }

    private void sendUdp(byte[] srcIp, byte[] dstIp, int srcPort, int dstPort, byte[] data, int len) throws Exception {
        int total = 20 + 8 + len; byte[] p = new byte[total]; buildIp(p, total, 17, srcIp, dstIp);
        int u = 20; put16(p,u,srcPort); put16(p,u+2,dstPort); put16(p,u+4,8+len); put16(p,u+6,0); if (len>0) System.arraycopy(data,0,p,u+8,len); put16(p,u+6,tcpUdpChecksum(p,srcIp,dstIp,17,u,8+len)); writePacket(p);
    }

    private void buildIp(byte[] p, int total, int proto, byte[] src, byte[] dst) {
        p[0]=0x45; p[1]=0; put16(p,2,total); put16(p,4,ipId.incrementAndGet()); put16(p,6,0x4000); p[8]=64; p[9]=(byte)proto; System.arraycopy(src,0,p,12,4); System.arraycopy(dst,0,p,16,4); put16(p,10,0); put16(p,10,checksum(p,0,20));
    }

    private int tcpUdpChecksum(byte[] p, byte[] src, byte[] dst, int proto, int off, int len) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(); try { b.write(src); b.write(dst); b.write(0); b.write(proto); b.write((len>>8)&255); b.write(len&255); b.write(p,off,len); if ((len&1)==1) b.write(0); } catch (Throwable ignored) {}
        byte[] arr = b.toByteArray(); return checksum(arr,0,arr.length);
    }
    private int checksum(byte[] b, int off, int len) { long sum=0; for(int i=off;i<off+len;i+=2){ int v=(b[i]&255)<<8; if(i+1<off+len)v|=b[i+1]&255; sum+=v; while((sum>>16)>0)sum=(sum&0xffff)+(sum>>16);} return (int)(~sum)&0xffff; }
    private void writePacket(byte[] p) throws Exception { synchronized (writeLock) { if (out != null) { out.write(p); out.flush(); } } }
    private static int u16(byte[] p,int o){return ((p[o]&255)<<8)|(p[o+1]&255);} private static long u32(byte[] p,int o){return ((long)(p[o]&255)<<24)|((long)(p[o+1]&255)<<16)|((long)(p[o+2]&255)<<8)|(p[o+3]&255);} private static void put16(byte[] p,int o,int v){p[o]=(byte)(v>>8);p[o+1]=(byte)v;} private static void put32(byte[] p,int o,long v){p[o]=(byte)(v>>24);p[o+1]=(byte)(v>>16);p[o+2]=(byte)(v>>8);p[o+3]=(byte)v;} private static String ip(byte[] a){return (a[0]&255)+"."+(a[1]&255)+"."+(a[2]&255)+"."+(a[3]&255);} private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}} private void close(Socket s){if(s!=null)try{s.close();}catch(Throwable ignored){}}

    static class TcpConn { String key, host; byte[] clientIp, remoteIp; int clientPort, remotePort; long clientNext, serverSeq; Socket remote; volatile boolean connected, firstSent; java.util.ArrayList<byte[]> pending = new java.util.ArrayList<>(); }
}

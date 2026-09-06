package app.yuro.guard;

public class BucketRow {
    public long bucket;
    public long rx;
    public long tx;
    public long total() { return Math.max(0, rx) + Math.max(0, tx); }
}

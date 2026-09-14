package app.yoru.mobile;

import java.util.HashSet;

final class GenreIdentity {
    private GenreIdentity(){}
    static String[] match(String[] selected,String[][] candidates){
        HashSet<String> names=new HashSet<>();
        if(selected==null||selected.length<2||candidates==null)return null;
        for(int i=1;i<selected.length;i++){String name=ApiRepository.plainName(selected[i]);if(!name.isEmpty())names.add(name);}
        String[] found=null;
        for(String[] candidate:candidates){
            if(candidate==null||candidate.length<2||candidate[0]==null||candidate[0].isEmpty())continue;
            boolean matches=false;
            for(int i=1;i<candidate.length;i++)if(candidate[i]!=null&&names.contains(ApiRepository.plainName(candidate[i]))){matches=true;break;}
            if(!matches)continue;
            if(found!=null&&!found[0].equals(candidate[0]))return null;
            found=candidate;
        }
        return found;
    }
}

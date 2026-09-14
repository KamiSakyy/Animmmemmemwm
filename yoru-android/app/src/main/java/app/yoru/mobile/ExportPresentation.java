package app.yoru.mobile;

import androidx.work.Data;
import androidx.work.WorkInfo;
import java.util.List;

final class ExportPresentation {
    private ExportPresentation(){}

    static WorkInfo latest(List<WorkInfo> jobs,String download){
        WorkInfo selected=null;long newest=-1;
        for(WorkInfo job:jobs){
            if(!job.getTags().contains(DocumentDownloads.EXPORT_ITEM_TAG+download))continue;
            long created=job.getOutputData().getLong("updated",0);
            for(String tag:job.getTags())if(tag.startsWith(DocumentDownloads.EXPORT_CREATED_TAG)){
                try{created=Long.parseLong(tag.substring(DocumentDownloads.EXPORT_CREATED_TAG.length()));}catch(NumberFormatException ignored){}
                break;
            }
            if(selected==null||(!job.getState().isFinished()&&selected.getState().isFinished())||(job.getState().isFinished()==selected.getState().isFinished()&&created>newest)){selected=job;newest=created;}
        }
        return selected;
    }

    static String status(WorkInfo job){
        if(job==null)return "";
        switch(job.getState()){
            case RUNNING:return "prepare".equals(job.getProgress().getString("stage"))?"Подготавливаем видеофайл без перекодирования":"Сохраняем в выбранную папку";
            case ENQUEUED:return job.getRunAttemptCount()>0?"Сохранение будет повторено":"Ожидает сохранения в папку";
            case BLOCKED:return "Ожидает завершения предыдущего сохранения";
            case SUCCEEDED:return "Файл сохранён в выбранную папку";
            case CANCELLED:return "Сохранение остановлено · видео осталось в YORU";
            case FAILED:
                String reason=job.getOutputData().getString("result");
                if("permission".equals(reason))return "Нужен доступ к папке · выберите её заново";
                if("space".equals(reason))return "Недостаточно места для сохранения файла";
                if("unsupported".equals(reason))return "Этот вариант доступен только внутри YORU";
                return "Файл не сохранён · можно повторить";
            default:return "";
        }
    }

    static int percent(WorkInfo job){return job!=null&&job.getState()==WorkInfo.State.RUNNING?job.getProgress().getInt("percent",-1):-1;}
    static String size(WorkInfo job){
        if(job==null||job.getState()!=WorkInfo.State.RUNNING)return "";
        Data progress=job.getProgress();long copied=progress.getLong("copied",0),total=progress.getLong("total",-1);
        return copied>0?"Сохранено "+MediaSize.label(copied)+(total>0?" из "+MediaSize.label(total):""):"";
    }
}

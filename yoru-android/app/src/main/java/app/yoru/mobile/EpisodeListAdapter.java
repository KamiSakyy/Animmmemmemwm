package app.yoru.mobile;

import android.app.Activity;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;

/** Real holders, stable IDs and no full row teardown during a metadata update. */
final class EpisodeListAdapter
  extends ListAdapter<EpisodeRow, EpisodeListAdapter.Holder>
{

  interface Listener {
    void watch(EpisodeRow row);
    void download(EpisodeRow row);
    void markWatched(EpisodeRow row);
  }

  private final Activity activity;
  private final Listener listener;
  private final String animeKey;

  EpisodeListAdapter(Activity activity, String animeKey, Listener listener) {
    super(
      new DiffUtil.ItemCallback<EpisodeRow>() {
        @Override
        public boolean areItemsTheSame(EpisodeRow old, EpisodeRow next) {
          return old.stableId() == next.stableId();
        }

        @Override
        public boolean areContentsTheSame(EpisodeRow old, EpisodeRow next) {
          return old.equals(next);
        }
      }
    );
    this.activity = activity;
    this.animeKey = animeKey;
    this.listener = listener;
    setHasStableIds(true);
  }

  @Override
  public long getItemId(int position) {
    return getItem(position).stableId();
  }

  @Override
  public Holder onCreateViewHolder(ViewGroup parent, int type) {
    return new Holder();
  }

  @Override
  public void onBindViewHolder(Holder holder, int position) {
    holder.bind(getItem(position));
  }

  @Override
  public void onViewRecycled(Holder holder) {
    holder.clearImage();
    holder.bound = null;
    super.onViewRecycled(holder);
  }

  final class Holder extends RecyclerView.ViewHolder {

    final LinearLayout card;
    final ImageView image;
    final TextView title, availability, date, play, download;
    EpisodeRow bound;
    boolean imageBound;

    Holder() {
      super(Ui.column(activity));
      LinearLayout outer = (LinearLayout) itemView;
      outer.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
      outer.setPadding(0, 0, 0, Ui.dp(activity, 8));
      card = Ui.row(activity);
      card.setPadding(
        Ui.dp(activity, 10),
        Ui.dp(activity, 9),
        Ui.dp(activity, 10),
        Ui.dp(activity, 9)
      );
      card.setBackground(Ui.stroke(Ui.CARD, 14, activity));
      FrameLayout preview = new FrameLayout(activity);
      preview.setBackground(Ui.shape(Ui.SURFACE, 12, activity));
      preview.setClipToOutline(true);
      image = new ImageView(activity);
      image.setScaleType(ImageView.ScaleType.CENTER_CROP);
      preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
      date = Ui.text(activity, "", 10, Ui.PURPLE, true);
      date.setGravity(Gravity.CENTER);
      date.setMaxLines(3);
      date.setPadding(Ui.dp(activity, 4), 0, Ui.dp(activity, 4), 0);
      preview.addView(date, new FrameLayout.LayoutParams(-1, -1));
      play = Ui.text(activity, "▶", 17, 0xeeffffff, true);
      play.setGravity(Gravity.CENTER);
      play.setBackground(Ui.shape(0x66000000, 24, activity));
      preview.addView(
        play,
        new FrameLayout.LayoutParams(
          Ui.dp(activity, 34),
          Ui.dp(activity, 34),
          Gravity.CENTER
        )
      );
      LinearLayout.LayoutParams imageParams = Ui.lp(activity, 94, 56);
      imageParams.rightMargin = Ui.dp(activity, 11);
      card.addView(preview, imageParams);
      LinearLayout text = Ui.column(activity);
      title = Ui.text(activity, "", 12, Ui.TEXT, true);
      title.setMaxLines(2);
      title.setEllipsize(TextUtils.TruncateAt.END);
      availability = Ui.text(activity, "", 10, Ui.MUTED, false);
      availability.setMaxLines(2);
      text.addView(title);
      Ui.space(text, 4);
      text.addView(availability);
      card.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
      download = Ui.text(activity, "", 10, Ui.PURPLE, true);
      download.setMaxLines(2);
      download.setGravity(Gravity.CENTER);
      download.setPadding(
        Ui.dp(activity, 8),
        Ui.dp(activity, 7),
        Ui.dp(activity, 8),
        Ui.dp(activity, 7)
      );
      download.setBackground(Ui.stroke(Ui.SURFACE, 10, activity));
      Ui.press(download);
      Ui.press(card);
      card.addView(download);
      outer.addView(card);
      card.setOnClickListener(view -> {
        if (bound != null) listener.watch(bound);
      });
      download.setOnClickListener(view -> {
        if (bound != null && !bound.future) listener.download(bound);
      });
      card.setOnLongClickListener(view -> {
        if (bound == null || bound.future) return false;
        listener.markWatched(bound);
        return true;
      });
    }

    void bind(EpisodeRow row) {
      EpisodeRow previous = bound;
      bound = row;
      title.setText(
        (row.current ? "▶ " : "") +
          "Серия " +
          Ui.number(row.number) +
          (row.name.isEmpty() ? "" : " · " + row.name)
      );
      title.setTextColor(row.current ? Ui.PURPLE : Ui.TEXT);
      availability.setText(
        row.status() +
          (!row.future && row.duration > 0 ? " · " + Ui.time(row.duration) : "")
      );
      availability.setTextColor(row.future ? Ui.MUTED : 0xffa9e8c2);
      date.setText(row.date.replace(", ", "\n"));
      date.setVisibility(row.future ? View.VISIBLE : View.GONE);
      image.setVisibility(row.future ? View.GONE : View.VISIBLE);
      play.setVisibility(row.future ? View.GONE : View.VISIBLE);
      download.setEnabled(!row.future);
      download.setAlpha(row.future ? .55f : 1f);
      download.setText(
        row.future
          ? "Не вышла"
          : row.downloadId.isEmpty()
            ? row.downloadLabel
            : "Скачана"
      );
      download.setTextColor(row.future ? Ui.MUTED : Ui.PURPLE);
      card.setContentDescription(
        "Серия " +
          Ui.number(row.number) +
          ". " +
          row.status() +
          (row.future ? ". " + row.date : "")
      );
      if (row.future) clearImage();
      else if (
        !imageBound ||
        previous == null ||
        previous.stableId() != row.stableId() ||
        !previous.poster.equals(row.poster)
      ) {
        YoruApp.app().images.load(
          image,
          row.poster,
          animeKey + ":ep:" + Ui.number(row.number)
        );
        imageBound = true;
      }
    }

    void clearImage() {
      if (imageBound) YoruApp.app().images.cancel(image);
      imageBound = false;
    }
  }
}

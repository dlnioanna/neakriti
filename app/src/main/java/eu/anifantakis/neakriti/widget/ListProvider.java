package eu.anifantakis.neakriti.widget;

import java.util.ArrayList;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService.RemoteViewsFactory;

import eu.anifantakis.neakriti.R;
import eu.anifantakis.neakriti.utils.AppUtils;

// source: https://laaptu.wordpress.com/2013/07/19/android-app-widget-with-listview/
public class ListProvider implements RemoteViewsFactory {
    public static class ListItem {
        public String heading,imageUrl;

    }

    private ArrayList<ListItem> listItemList = new ArrayList<ListItem>();
    private Context context = null;
    private int appWidgetId;

    public ListProvider(Context context, Intent intent) {
        this.context = context;
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);

        populateListItem();
    }

    private void populateListItem() {
        /*
        for (int i = 0; i < 10; i++) {
            ListItem listItem = new ListItem();
            listItem.heading = "Heading" + i;
            listItemList.add(listItem);
        }
        */


        listItemList = (ArrayList<ListItem>)
                WidgetFetchArticlesService.listItemList
                        .clone();

    }

    @Override
    public int getCount() {
        return listItemList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    /*
     *Similar to getView of Adapter where instead of View
     *we return RemoteViews
     *
     */
    @Override
    public RemoteViews getViewAt(int position) {
        final RemoteViews remoteView = new RemoteViews(
                context.getPackageName(), R.layout.row_widget_list);
        ListItem listItem = listItemList.get(position);
        remoteView.setTextViewText(R.id.widget_row_heading, listItem.heading);
        remoteView.setImageViewBitmap(R.id.widget_row_image, AppUtils.getBitmapfromUrl(listItem.imageUrl));

        return remoteView;
    }


    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onCreate() {
    }

    @Override
    public void onDataSetChanged() {
    }

    @Override
    public void onDestroy() {
    }

}
package eu.anifantakis.neakriti;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.gms.analytics.HitBuilders;
import com.google.android.gms.analytics.Tracker;
import com.google.firebase.FirebaseApp;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

import eu.anifantakis.neakriti.data.ArticlesListAdapter;
import eu.anifantakis.neakriti.data.RequestInterface;
import eu.anifantakis.neakriti.data.StorageIntentService;
import eu.anifantakis.neakriti.data.StorageRetrievalAsyncTask;
import eu.anifantakis.neakriti.data.db.ArticlesDBContract;
import eu.anifantakis.neakriti.data.feed.ArticlesCollection;
import eu.anifantakis.neakriti.data.feed.gson.Article;
import eu.anifantakis.neakriti.data.feed.gson.Feed;
import eu.anifantakis.neakriti.databinding.ActivityArticleListBinding;
import eu.anifantakis.neakriti.preferences.SetPrefs;
import eu.anifantakis.neakriti.utils.AppUtils;
import eu.anifantakis.neakriti.utils.NeaKritiApp;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import static android.support.v4.app.NotificationCompat.VISIBILITY_PUBLIC;
import static eu.anifantakis.neakriti.utils.AppUtils.URL_BASE;
import static eu.anifantakis.neakriti.utils.AppUtils.isNightMode;
import static eu.anifantakis.neakriti.utils.AppUtils.mNotificationManager;
import static eu.anifantakis.neakriti.utils.AppUtils.onlineMode;
import static eu.anifantakis.neakriti.utils.AppUtils.spec;
import static eu.anifantakis.neakriti.utils.NeaKritiApp.sharedPreferences;


public class ArticleListActivity extends AppCompatActivity implements
        NavigationView.OnNavigationItemSelectedListener,
        ArticlesListAdapter.ArticleItemClickListener,
        LoaderManager.LoaderCallbacks<ArticlesCollection>,
        SwipeRefreshLayout.OnRefreshListener,
        Player.EventListener{

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private ActivityArticleListBinding binding;
    private ArticlesListAdapter mArticlesListAdapter;
    private RecyclerView mRecyclerView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Retrofit retrofit;
    private ViewGroup liveView;
    private static ArticlesCollection cachedCollection = null;
    private ImageView btnRadio;
    private  boolean clickedAtLeastOneItem = false;
    private static boolean exoPlayerIsPlaying = false;
    private TextView feedCategoryTitle;
    private ItemTouchHelper itemTouchHelper;
    private ArticleDetailFragment fragment;
    private Tracker mTracker;
    private SimpleExoPlayer mRadioPlayer;
    private NavigationView navigationView;

    private static final int ARTICLES_FEED_LOADER = 0;
    private static final String LOADER_TITLE = "LOADER_TITLE";
    private static final String LOADER_TYPE = "LOADER_TYPE";
    private static final String LOADER_ID = "LOADER_ID";
    private static final String LOADER_ITEMS_COUNT = "LOADER_ITEMS_COUNT";
    private static final String LIVE_PANEL_VISIBILITY = "LIVE_PANEL_VISIBILITY";
    private static final String STATE_EXO_PLAYER_RADIO_PLAYING = "exo_player_radio_playing";
    private static final String STATE_CLICKED_AN_ITEM = "STATE_CLICKED_AN_ITEM";
    private static final String STATE_FRAGMENT = "STATE_FRAGMENT";

    public static final int DETAIL_ACTIVITY_REQUEST_CODE = 100;
    public static final int PREFERENCES_REQUEST_CODE = 200;

    public static Bitmap bm;

    // we need to reload everytime the activity loads onResume, except of when it occurs when coming back from Detail Activity
    public static boolean shouldreload = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_article_list);
        //setContentView(R.layout.activity_article_list);

        mTracker = ((NeaKritiApp) getApplication()).getDefaultTracker();
        navigationView = binding.navView;

        FirebaseApp.initializeApp(this);
        // get the shared preferences and apply their settings where needed
        setCategoryAvailabilities();

        Toolbar toolbar = binding.masterView.toolbar;
        setSupportActionBar(toolbar);
        //toolbar.setTitle(getTitle());

        feedCategoryTitle = binding.masterView.articles.incLivePanel.feedCategoryTitle;
        btnRadio = binding.masterView.articles.incLivePanel.btnLiveRadio;
        initializeRadioExoPlayer();

        liveView = binding.masterView.articles.incLivePanel.liveView;

        if (savedInstanceState!=null){
            //cachedCollection = savedInstanceState.getParcelable(CACHED_COLLECTION);
            liveView.setVisibility(savedInstanceState.getInt(LIVE_PANEL_VISIBILITY));

            if (savedInstanceState.containsKey(STATE_EXO_PLAYER_RADIO_PLAYING)) {
                exoPlayerIsPlaying = savedInstanceState.getBoolean(STATE_EXO_PLAYER_RADIO_PLAYING);
            }
            if (exoPlayerIsPlaying){
                btnRadio.setImageResource(R.drawable.btn_radio_pause);
            }
            if (savedInstanceState.containsKey(STATE_CLICKED_AN_ITEM)) {
                clickedAtLeastOneItem = savedInstanceState.getBoolean(STATE_CLICKED_AN_ITEM);
            }

            if (savedInstanceState.containsKey(STATE_FRAGMENT)){
                fragment = (ArticleDetailFragment) getSupportFragmentManager().getFragment(savedInstanceState, STATE_FRAGMENT);
            }

            feedSrvid = savedInstanceState.getString(STATE_FEED_SRVID);
            feedItems = savedInstanceState.getInt(STATE_FEED_ITEMS);
            feedName = savedInstanceState.getString(STATE_FEED_NAME);
            feedType = savedInstanceState.getInt(STATE_FEED_TYPE);
        }
        else{
            // set default selected menu category the home category
            navigationView.setCheckedItem(R.id.nav_home);
            feedSrvid = getString(R.string.nav_home_id);
            feedItems = 25;
            feedName = getString(R.string.nav_home);
            feedType = ArticlesDBContract.DB_TYPE_CATEGORY;
            liveView.setVisibility(View.GONE);
        }

        if (binding.masterView.articles.articleDetailContainer != null){
            // The detail container view will be present only in the
            // large-screen layouts (res/values-w900dp).
            // If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;
        }
        else{
            // in phone edition we just want portrait mode
            //this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (mTwoPane && !clickedAtLeastOneItem){
            assert binding.masterView.articles.articleDetailContainer != null;
            binding.masterView.articles.articleDetailContainer.setVisibility(View.GONE);
        }

        DrawerLayout drawer = binding.drawerLayout;
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = binding.navView;
        navigationView.setNavigationItemSelectedListener(this);

        mSwipeRefreshLayout = binding.masterView.articles.mainLayoutSwipe;
        mSwipeRefreshLayout.setOnRefreshListener(this);

        Log.d("ARTICLES FETCH", "FETCH DATA FROM " + URL_BASE);

        // SETUP RETROFIT
        retrofit = new Retrofit.Builder()
                .baseUrl(URL_BASE)
                .client(new OkHttpClient.Builder().connectionSpecs(Collections.singletonList(spec)).build())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // SETUP RECYCLER VIEW
        mRecyclerView = binding.masterView.articles.articleList;
        //assert mRecyclerView != null;

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(layoutManager);

        //mRecyclerView.setHasFixedSize(true);
        mArticlesListAdapter = new ArticlesListAdapter(this);
        mRecyclerView.setAdapter(mArticlesListAdapter);

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int id = (int) viewHolder.itemView.getTag();

                Uri uri = ArticlesDBContract.ArticleEntry.FAVORITE_CONTENT_URI;
                uri = uri.buildUpon().appendPath(String.valueOf(id)).build();
                getContentResolver().delete(uri, null, null);

                cachedCollection = null;

                // prepare global values to be loaded when "onResume" is called
                feedType = ArticlesDBContract.DB_TYPE_FAVORITE;
                feedSrvid = "0";
                feedItems = 200;
            }
        });

        shouldreload = false;
        makeArticlesLoaderQuery(feedName, feedType, feedSrvid, feedItems);
    }


    /**
     * Interface implementation (defined in the adapter) for the clicking of an item in the articles recycler view
     * @param clickedItemIndex The index that refers to the selected item
     * @param sharedImage the ImageView of the selected article
     */
    @Override
    public void onArticleItemClick(int clickedItemIndex, ImageView sharedImage) {
        Log.d("RV ACTION", "ITEM CLICK");

        String title = mArticlesListAdapter.getArticleAtIndex(clickedItemIndex).getTitle();
        Article article = mArticlesListAdapter.getArticleAtIndex(clickedItemIndex);

        // analytics track category id select
        mTracker.setScreenName("ARTICLE - " + title);
        mTracker.send(new HitBuilders.ScreenViewBuilder().build());

        // display in the Two Pane version (tablet) the middle area where the article will be displayed
        if (mTwoPane && !clickedAtLeastOneItem) {
            binding.masterView.articles.articleDetailContainer.setVisibility(View.VISIBLE);
            clickedAtLeastOneItem = true;
        }

        if (mTwoPane) {
            Bundle arguments = new Bundle();
            arguments.putParcelable(AppUtils.EXTRAS_ARTICLE, article);
            fragment = new ArticleDetailFragment();
            fragment.setArguments(arguments);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.article_detail_container, fragment)
                    .commit();
        } else {
            Intent intent = new Intent(this, ArticleDetailActivity.class);
            article.setGroupName(feedName);
            intent.putExtra(AppUtils.EXTRAS_ARTICLE, article);

            //AppUtils.saveToInternalStorage(getApplicationContext(), ((BitmapDrawable)sharedImage.getDrawable()).getBitmap(), "images", "current_thumb.jpg");
            if (sharedImage != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    Log.d("TRANSITION NAME", ViewCompat.getTransitionName(sharedImage));
                }

                bm = ((BitmapDrawable) sharedImage.getDrawable()).getBitmap();
                intent.putExtra(AppUtils.EXTRAS_LOW_RES_BITMAP, true);
            }

            // bundle for the transition effect
            Bundle bundle;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && sharedImage!=null) {
                bundle = ActivityOptionsCompat
                        .makeSceneTransitionAnimation(
                                this,
                                sharedImage,
                                Objects.requireNonNull(ViewCompat.getTransitionName(sharedImage)) //sharedImage.getTransitionName()
                        ).toBundle();

                //if (feedType == ArticlesDBContract.DB_TYPE_FAVORITE){
                    ActivityCompat.startActivityForResult(this, intent, DETAIL_ACTIVITY_REQUEST_CODE, bundle);
                //}
                //else {
                //    startActivity(intent, bundle);
                //}
            }
            else{
                //if (feedType == ArticlesDBContract.DB_TYPE_FAVORITE) {
                    startActivityForResult(intent, DETAIL_ACTIVITY_REQUEST_CODE);
                //}
                //else {
                //    startActivity(intent);
                //}
            }
        }
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // if the theme has changed from day to night (or vice versa) but this activity uses the opposite theme, then recreate.
        if (resultCode == Activity.RESULT_OK) {
            if (data.getBooleanExtra("REPAINT", true)) {
                Log.d("REPAINT", "YES!");
                recreate();
            }
        }

        // if we are back from another activity and the type is favorites, refresh because we might
        // have removed an item from the favorite list
        if (requestCode==DETAIL_ACTIVITY_REQUEST_CODE){
            if (feedType == ArticlesDBContract.DB_TYPE_FAVORITE){
                cachedCollection = null;
                makeArticlesLoaderQuery(feedName, ArticlesDBContract.DB_TYPE_FAVORITE, null, feedItems);
            }
        }
        else if (requestCode==PREFERENCES_REQUEST_CODE) {
            // on resume check if any preferences have changed, and if so
            setCategoryAvailabilities();

            // the "recreate()" method forces new theme to be applied in case we have switched from day to night theme (or vice versa)
            recreate();
        }
    }

    /**
     * Show or hide the categories based on the user's preferences
     */
    private void setCategoryAvailabilities()
    {
        Menu menu = navigationView.getMenu();
        menu.findItem(R.id.nav_crete).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_crete_id), true));
        menu.findItem(R.id.nav_views).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_views_id), true));
        menu.findItem(R.id.nav_economy).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_economy_id), true));
        menu.findItem(R.id.nav_culture).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_culture_id), true));
        menu.findItem(R.id.nav_pioneering).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_pioneering_id), true));
        menu.findItem(R.id.nav_sports).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_sports_id), true));
        menu.findItem(R.id.nav_lifestyle).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_lifestyle_id), true));
        menu.findItem(R.id.nav_health).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_health_id), true));
        menu.findItem(R.id.nav_woman).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_woman_id), true));
        menu.findItem(R.id.nav_travel).setVisible(sharedPreferences.getBoolean(getString(R.string.nav_travel_id), true));
    }


    private void makeArticlesLoaderQuery(final String title, final int type, String id, int items){
        if (id==null){
            // if we load bookmarks, then category id is redundant
            // There are no categories in bookmarks, they are just bookmarks
            // so in that case where the "id" variable is set to null and we have to give just a value (we don't care what it is... just avoiding null pointers here)
            id = "0";
        }

        boolean isNetworkAvailable = AppUtils.isNetworkAvailable(this);
        // if we knew we were in online mode, but discovered that there is no network (going offline for the first time)
        if (AppUtils.onlineMode && !isNetworkAvailable){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setTitle(R.string.dlg_no_network_title)
                    .setMessage(R.string.dlg_no_network_body)
                    .setIcon(R.drawable.cloud_off_48px)
                    .setNegativeButton(R.string.dlg_no_network_offline_mode, (dialog, id1) -> {

                    })
                    .setPositiveButton(R.string.dlg_exit, (dialog, id12) -> finish());
            // Create the AlertDialog object and return it
            builder.create().show();
        }
        AppUtils.onlineMode = isNetworkAvailable;

        feedCategoryTitle.setText(feedName);

        if (onlineMode) {
            // analytics track category id select
            mTracker.setScreenName("MAIN - " + id);
            mTracker.send(new HitBuilders.ScreenViewBuilder().build());

            Bundle bundle = new Bundle();
            bundle.putString(LOADER_TITLE, title);
            bundle.putInt(LOADER_TYPE, type);
            bundle.putString(LOADER_ID, id);
            bundle.putInt(LOADER_ITEMS_COUNT, items);

            Loader<Feed> loader = getSupportLoaderManager().getLoader(ARTICLES_FEED_LOADER);
            if (loader == null) {
                getSupportLoaderManager().initLoader(ARTICLES_FEED_LOADER, bundle, this);
            } else {
                getSupportLoaderManager().restartLoader(ARTICLES_FEED_LOADER, bundle, this);
            }
        }
        else{
            // handle offline data, fetch articles from the database for the given category
            new StorageRetrievalAsyncTask(collection -> {
                mSwipeRefreshLayout.setRefreshing(false);
                cachedCollection = collection;
                mArticlesListAdapter.setCollection(collection);
                mArticlesListAdapter.notifyDataSetChanged();
                mRecyclerView.smoothScrollToPosition(0);
                Log.d("ASYNC TASK", "FETCHING DATABASE DATA");
            }).execute(this, id, title, type);
        }
    }

    @SuppressLint("StaticFieldLeak")
    @NonNull
    @Override
    public Loader<ArticlesCollection> onCreateLoader(int i, final Bundle bundle) {
        Log.d("LOADING", "ON CREATE LOADER");
        return new AsyncTaskLoader<ArticlesCollection>(this) {
            @Override
            public ArticlesCollection loadInBackground() {
                Feed feed;
                int fetchType = bundle.getInt(LOADER_TYPE);

                // Here in bookmarks we combine Loader with Content Provider to fetch data from the SQLite Database
                if (fetchType == ArticlesDBContract.DB_TYPE_FAVORITE){
                    Cursor cursor = getContentResolver().query(ArticlesDBContract.ArticleEntry.CONTENT_URI,
                            null,
                            ArticlesDBContract.ArticleEntry.COL_TYPE + " = " + ArticlesDBContract.DB_TYPE_FAVORITE,
                            null,
                            ArticlesDBContract.ArticleEntry._ID + " DESC"
                    );

                    if (cursor!=null) {
                        if (cursor.getCount() > 0) {
                            ArticlesCollection favoriteArticles = new ArticlesCollection(bundle.getString(LOADER_TITLE), ArticlesDBContract.DB_TYPE_FAVORITE, bundle.getString(LOADER_ID));

                            cursor.moveToFirst();
                            while (!cursor.isAfterLast()){
                                Article article = new Article();

                                article.setGuid(cursor.getInt(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_GUID)));
                                article.setLink(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_LINK)));
                                article.setTitle(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_TITLE)));
                                article.setDescription(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_DESCRIPTION)));
                                article.setPubDateStr(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_PUB_DATE_STR)));
                                article.setUpdatedStr(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_UPDATED_STR)));
                                article.setPubDateGre(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_PUB_DATE_GRE)));
                                article.setImgThumb(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_IMG_THUMB)));
                                article.setImgLarge(cursor.getString(cursor.getColumnIndex(ArticlesDBContract.ArticleEntry.COL_IMG_LARGE)));

                                favoriteArticles.addArticle(article);
                                cursor.moveToNext();
                            }
                            cursor.close();
                            // all results of the favorites are contained in a single page (no endless load here)
                            return favoriteArticles;
                        }
                        else{
                            cursor.close();
                            return null;
                        }
                    }
                }
                else {
                    // Here we combine Loader with Retrofit call to fetch data from the web
                    try {
                        RequestInterface request = retrofit.create(RequestInterface.class);
                        Call<Feed> call = request.getFeedByCategory(bundle.getString(LOADER_ID), bundle.getInt(LOADER_ITEMS_COUNT));
                        // make a synchronous retrofit call since we are already outside main thread in loader (AsyncTaskLoader), so async in async doesn't make much sense.
                        feed = call.execute().body();

                    } catch (IOException e) {
                        Log.e("LOG EXCEPTION", "RETROFIT CALL");
                        e.printStackTrace();
                        return null;
                    }

                    // We have internet connection but the server is not responding (SERVER DOWN)
                    if (feed==null){
                        Log.d("LOADER", "SERVER NOT RESPONDING");
                        return null;
                    }
                    else {

                        ArticlesCollection result = new ArticlesCollection(feed.getChannel().getItems(), bundle.getString(LOADER_TITLE), ArticlesDBContract.DB_TYPE_CATEGORY, bundle.getString(LOADER_ID));
                        storeForOfflineUsageCollection(result);
                        return result;
                    }
                }
                return null;
            }


            @Override
            protected void onStartLoading() {
                if (cachedCollection == null) {
                    Log.d("LOADER", "FETCHING NEW DATA");
                    mSwipeRefreshLayout.setRefreshing(true);
                    forceLoad();
                } else {
                    Log.d("LOADER", "SHOWING CACHED DATA");
                    deliverResult(cachedCollection);
                }
            }

            @Override
            public void deliverResult(ArticlesCollection data) {
                binding.activityArticleListLogo.setVisibility(View.GONE);

                if (cachedCollection == null) {
                    mArticlesListAdapter.notifyDataSetChanged();
                    mRecyclerView.smoothScrollToPosition(0);
                }

                cachedCollection = data;
                mArticlesListAdapter.setCollection(data);

                // if loader returned null data (aka SERVER WAS DOWN AND DIDN'T RESPOND)
                if (data == null && feedType!=ArticlesDBContract.DB_TYPE_FAVORITE){
                    AlertDialog.Builder builder = new AlertDialog.Builder(ArticleListActivity.this);
                    builder
                            .setTitle(R.string.dlg_server_down_title)
                            .setMessage(R.string.dlg_server_down_body)
                            .setIcon(R.drawable.sync_problem_48px)
                            .setNegativeButton(R.string.dlg_close, (dialog, id) -> finish());
                    // Create the AlertDialog object and return it
                    builder.create().show();
                }

                super.deliverResult(data);
            }
        };
    }

    /**
     * Store the category collection for offline usage by calling the IntentService that saves all
     * @param collection The ArticlesCollection that we need to store in the database
     */
    private void storeForOfflineUsageCollection(ArticlesCollection collection){
        Intent intent = new Intent(ArticleListActivity.this, StorageIntentService.class);
        intent.putExtra(StorageIntentService.COLLECTION, collection);
        startService(intent);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<ArticlesCollection> loader, ArticlesCollection data) {
        Log.d("LOADING", "LOAD FINISHED");

        mSwipeRefreshLayout.setRefreshing(false);
    }


    @Override
    public void onLoaderReset(@NonNull Loader<ArticlesCollection> loader) {

    }


    //============================ APP DRAWER =====================================

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = binding.drawerLayout;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        Log.d("MENU INFLATE", "DONE");
        getMenuInflater().inflate(R.menu.main, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        // show/hide the live streaming panel
        if (id == R.id.action_live){
            switchLivePanelVisibility();
        }

        //noinspection SimplifiableIfStatement
        else if (id == R.id.action_settings) {
            // if "settings" selected, open the Shared Preferences activity
            Intent itemintent = new Intent(this, SetPrefs.class);
            startActivityForResult(itemintent, PREFERENCES_REQUEST_CODE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Changes the livePanel's visibility from visible to gone with an animation
     */
    private void switchLivePanelVisibility(){
        liveView.clearAnimation();
        liveView.invalidate();

        if (liveView.getVisibility()==View.GONE){
            liveView.setVisibility(View.VISIBLE);
            AppUtils.setLayoutAnim_slidedown(liveView, false);
        }
        else{
            liveView.setVisibility(View.GONE);
            AppUtils.setLayoutAnim_slideup(liveView, true);
        }
    }

    private int feedType = -1;
    private String feedName = "";
    private String feedSrvid = "";
    private int feedItems = 0;

    private static final String STATE_FEED_TYPE = "STATE_FEED_TYPE";
    private static final String STATE_FEED_NAME = "STATE_FEED_NAME";
    private static final String STATE_FEED_SRVID = "STATE_FEED_SRVID";
    private static final String STATE_FEED_ITEMS = "STATE_FEED_ITEMS";

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();
        cachedCollection = null;
        itemTouchHelper.attachToRecyclerView(null);

        /*
        if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }else
         */

        if (id == R.id.nav_favorite_items){
            feedType = ArticlesDBContract.DB_TYPE_FAVORITE;
            feedItems = 200;
            feedName = item.getTitle().toString();
            makeArticlesLoaderQuery(feedName, ArticlesDBContract.DB_TYPE_FAVORITE, "0", feedItems);

            itemTouchHelper.attachToRecyclerView(mRecyclerView);
        }
        else {
            feedType = ArticlesDBContract.DB_TYPE_CATEGORY;
            feedItems = 25;
            feedName = item.getTitle().toString();

            if (id == R.id.nav_home)            { feedSrvid = getString(R.string.nav_home_id); }
            else if (id == R.id.nav_crete)      { feedSrvid = getString(R.string.nav_crete_id); }
            else if (id == R.id.nav_views)      { feedSrvid = getString(R.string.nav_views_id); }
            else if (id == R.id.nav_economy)    { feedSrvid = getString(R.string.nav_economy_id); }
            else if (id == R.id.nav_culture)    { feedSrvid = getString(R.string.nav_culture_id); }
            else if (id == R.id.nav_pioneering) { feedSrvid = getString(R.string.nav_pioneering_id); }
            else if (id == R.id.nav_sports)     { feedSrvid = getString(R.string.nav_sports_id); }
            else if (id == R.id.nav_lifestyle)  { feedSrvid = getString(R.string.nav_lifestyle_id); }
            else if (id == R.id.nav_health)     { feedSrvid = getString(R.string.nav_health_id); }
            else if (id == R.id.nav_woman)      { feedSrvid = getString(R.string.nav_woman_id); }
            else if (id == R.id.nav_travel)     { feedSrvid = getString(R.string.nav_travel_id); }

            makeArticlesLoaderQuery(feedName, feedType, feedSrvid, feedItems);
        }

        DrawerLayout drawer = binding.drawerLayout;
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (isNightMode) {
            binding.activityArticleListLogo.setImageResource(R.drawable.logo_white);
            binding.activityArticleListFrame.setBackgroundColor(Color.parseColor("#333333"));
        }
        else {
            binding.activityArticleListLogo.setImageResource(R.drawable.logo);
            binding.activityArticleListFrame.setBackgroundColor(Color.WHITE);
        }

        if (shouldreload) {
            Log.d("RELOAD", "FORCE RELOAD");
            cachedCollection = null;
            makeArticlesLoaderQuery(feedName, feedType, feedSrvid, feedItems);
        }
        shouldreload = true;
    }

    @Override
    public void onRefresh() {
        cachedCollection = null;
        makeArticlesLoaderQuery(feedName, feedType, feedSrvid, feedItems);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        //outState.putParcelable(CACHED_COLLECTION, cachedCollection);
        outState.putInt(LIVE_PANEL_VISIBILITY, liveView.getVisibility());
        outState.putBoolean(STATE_EXO_PLAYER_RADIO_PLAYING, exoPlayerIsPlaying);
        outState.putBoolean(STATE_CLICKED_AN_ITEM, clickedAtLeastOneItem);

        outState.putString(STATE_FEED_SRVID, feedSrvid);
        outState.putInt(STATE_FEED_ITEMS, feedItems);
        outState.putString(STATE_FEED_NAME, feedName);
        outState.putInt(STATE_FEED_TYPE, feedType);

        if (fragment!=null) {
            getSupportFragmentManager().putFragment(outState, STATE_FRAGMENT, fragment);
        }
    }

    private boolean checkNetworkAvailabilityBeforeStreaming(){
        boolean isNetworkAvailable = (AppUtils.isNetworkAvailable(this));

        if (!isNetworkAvailable){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder
                    .setTitle(R.string.dlg_no_network_title)
                    .setMessage(R.string.dlg_no_network_stream_body)
                    .setIcon(R.drawable.cloud_off_48px)
                    .setNegativeButton(R.string.dlg_close, (dialog, id) -> {

                    });
            // Create the AlertDialog object and return it
            builder.create().show();
        }

        return isNetworkAvailable;
    }

    public void liveStreamTVClicked(View view){
        if (checkNetworkAvailabilityBeforeStreaming()) {
            // if no WiFi connected, warn the user for possible charges while watching streamed content
            if (!AppUtils.isWifiConnected(this)) {
                alertForStream(false);
            } else {
                streamTV();
            }
        }
    }

    public void liveStreamRadioClicked(View view){
        if (checkNetworkAvailabilityBeforeStreaming()) {
            if (!exoPlayerIsPlaying) {
                // if no WiFi connected, warn the user for possible charges while watching streamed content
                if (!AppUtils.isWifiConnected(this)) {
                    alertForStream(true);
                } else {
                    // in wifi no questions asked, just play ;)
                    streamRadioOnOff();
                }
            } else {
                streamRadioOnOff();
            }
        }
    }

    private static final int NOTIFICATION_RADIO984_ID = 235425424;

    /**
     * Plays/Stops Radio Stream
     */
    private void streamRadioOnOff(){
        exoPlayerIsPlaying = (mNotificationManager!=null);

        exoPlayerIsPlaying = !exoPlayerIsPlaying;
        setStreamRadioStatus(exoPlayerIsPlaying);
    }

    private void setStreamRadioStatus(boolean status){
        mRadioPlayer.setPlayWhenReady(status);

        if (status){
            Picasso.get()
                    .load(R.drawable.btn_radio_pause)
                    .into(btnRadio);

            String channelId = getString(R.string.notif_channel_radio_id);
            String channelName = getString(R.string.notif_channel_radio_name);
            int importance = 0;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                importance = NotificationManager.IMPORTANCE_LOW;
            }
            AppUtils.mNotificationManager = (NotificationManager) ArticleListActivity.this.getSystemService(Context.NOTIFICATION_SERVICE);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                NotificationChannel mChannel = new NotificationChannel(
                        channelId, channelName, importance);
                AppUtils.mNotificationManager.createNotificationChannel(mChannel);
            }

            Intent intent = new Intent(this, ArticleListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0 , intent,
                    PendingIntent.FLAG_ONE_SHOT);

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(getApplicationContext(), channelId)
                            .setSmallIcon(R.drawable.play_circle_outline_white)
                            .setContentTitle("Radio 984")
                            .setVisibility(VISIBILITY_PUBLIC)
                            .setAutoCancel(false)
                            .setContentText("Ακούτε Radio9.84 Live")
                            .setOngoing(true)
                            .setContentIntent(pendingIntent);

            AppUtils.mNotificationManager.notify(NOTIFICATION_RADIO984_ID, builder.build());

        }
        else{
            Picasso.get()
                    .load(R.drawable.btn_radio)
                    .into(btnRadio);

            if (AppUtils.mNotificationManager != null){
                AppUtils.mNotificationManager.cancel(NOTIFICATION_RADIO984_ID);
                AppUtils.mNotificationManager = null;
            }
        }
    }

    /**
     * Launches Activity that contains the TV Stream
     */
    private void streamTV(){
        // If radio is currently playing, ask the user to stop radio, or continue listening
        if (exoPlayerIsPlaying){
            DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        streamRadioOnOff();
                        Intent intent = new Intent(ArticleListActivity.this, TVStreamActivity.class);
                        startActivity(intent);
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            };

            new AlertDialog.Builder(this)
                    .setIcon(R.drawable.radio_48px)
                    .setTitle(getString(R.string.dlg_radio_playing_title))
                    .setMessage(getString(R.string.dlg_radio_playing_body))
                    .setPositiveButton(getString(R.string.dlg_continue), dialogClickListener)
                    .setNegativeButton(getString(R.string.dlg_cancel), dialogClickListener)
                    .create().show();
        }
        else {
            Intent intent = new Intent(this, TVStreamActivity.class);
            startActivity(intent);
        }
    }

    /**
     * Displays a warning when attempting to stream Audio/Video in LTE/3G mode rather than in WiFi.
     * @param forRadio if true, alerts and starts radio stream, if false alerts and starts tv stream
     */
    private void alertForStream(final boolean forRadio){
        DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    if (forRadio) {
                        streamRadioOnOff();
                    }
                    else{
                        streamTV();
                    }
                    break;

                case DialogInterface.BUTTON_NEGATIVE:
                    break;
            }
        };

        new AlertDialog.Builder(this)
                .setIcon(R.drawable.signal_wifi_off_48px)
                .setTitle(getString(R.string.dlg_no_wifi_detected_title))
                .setMessage(getString(R.string.dlg_no_wifi_detected_body))
                .setPositiveButton(getString(R.string.dlg_continue), dialogClickListener)
                .setNegativeButton(getString(R.string.dlg_cancel), dialogClickListener)
                .create().show();
    }

    private void initializeRadioExoPlayer(){
        mRadioPlayer = ((NeaKritiApp) getApplication()).getRadioPlayer();
        mRadioPlayer.addListener(this);
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest, int reason) {

    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }

    @Override
    public void onLoadingChanged(boolean isLoading) {

    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if((playbackState == Player.STATE_READY) && playWhenReady){
            Log.d("RssReader", "onPlayerStateChanged: PLAYING");
            exoPlayerIsPlaying=true;
            //makeNotification();
        } else if((playbackState == Player.STATE_READY)){
            Log.d("RssReader", "onPlayerStateChanged: PAUSED");
            exoPlayerIsPlaying=false;
            //if (mNotificationManager!=null)
            //    mNotificationManager.cancel(NOTIFICATION_RADIO984_ID);
        }
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {

    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {

    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        exoPlayerIsPlaying = false;
        setStreamRadioStatus(false);
    }

    @Override
    public void onPositionDiscontinuity(int reason) {

    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void onSeekProcessed() {

    }

    @Override
    public Resources.Theme getTheme() {
        Resources.Theme theme = super.getTheme();
        if(isNightMode){
            theme.applyStyle(R.style.NightAppTheme, true);
        }
        else{
            theme.applyStyle(R.style.AppTheme, true);
        }
        return super.getTheme();
    }
}

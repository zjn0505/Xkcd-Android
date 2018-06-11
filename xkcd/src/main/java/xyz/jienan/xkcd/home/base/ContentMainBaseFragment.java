package xyz.jienan.xkcd.home.base;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.Toast;

import com.squareup.seismic.ShakeDetector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import butterknife.BindView;
import butterknife.OnClick;
import butterknife.OnPageChange;
import xyz.jienan.xkcd.R;
import xyz.jienan.xkcd.base.BaseFragment;
import xyz.jienan.xkcd.comics.SearchCursorAdapter;
import xyz.jienan.xkcd.comics.dialog.NumberPickerDialogFragment;
import xyz.jienan.xkcd.list.XkcdListActivity;
import xyz.jienan.xkcd.model.XkcdPic;
import xyz.jienan.xkcd.ui.like.LikeButton;
import xyz.jienan.xkcd.ui.like.OnLikeListener;

import static android.content.Context.SENSOR_SERVICE;
import static android.support.v4.view.ViewPager.SCROLL_STATE_DRAGGING;
import static android.support.v4.view.ViewPager.SCROLL_STATE_IDLE;
import static android.view.HapticFeedbackConstants.CONTEXT_CLICK;
import static android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING;
import static butterknife.OnPageChange.Callback.PAGE_SCROLL_STATE_CHANGED;
import static butterknife.OnPageChange.Callback.PAGE_SELECTED;
import static xyz.jienan.xkcd.Const.FIRE_BROWSE_LIST_MENU;
import static xyz.jienan.xkcd.Const.FIRE_FAVORITE_OFF;
import static xyz.jienan.xkcd.Const.FIRE_FAVORITE_ON;
import static xyz.jienan.xkcd.Const.FIRE_FROM_NOTIFICATION;
import static xyz.jienan.xkcd.Const.FIRE_FROM_NOTIFICATION_INDEX;
import static xyz.jienan.xkcd.Const.FIRE_NEXT_BAR;
import static xyz.jienan.xkcd.Const.FIRE_PREVIOUS_BAR;
import static xyz.jienan.xkcd.Const.FIRE_SEARCH;
import static xyz.jienan.xkcd.Const.FIRE_SHAKE;
import static xyz.jienan.xkcd.Const.FIRE_SPECIFIC_MENU;
import static xyz.jienan.xkcd.Const.FIRE_THUMB_UP;
import static xyz.jienan.xkcd.Const.INVALID_ID;
import static xyz.jienan.xkcd.Const.LAST_VIEW_XKCD_ID;
import static xyz.jienan.xkcd.Const.PREF_ARROW;
import static xyz.jienan.xkcd.Const.XKCD_INDEX_ON_NOTI_INTENT;

public abstract class ContentMainBaseFragment extends BaseFragment implements ShakeDetector.Listener {

    protected static final int REQ_LIST_ACTIVITY = 10;

    @BindView(R.id.viewpager)
    protected ViewPager viewPager;

    @BindView(R.id.fab)
    public FloatingActionButton fab;

    @BindView(R.id.btn_fav)
    protected LikeButton btnFav;

    @BindView(R.id.btn_thumb)
    protected LikeButton btnThumb;

    protected BaseStatePagerAdapter adapter;

    protected ShakeDetector sd;

    private SharedPreferences sharedPreferences;

    protected int latestIndex = INVALID_ID;

    protected int lastViewdId = INVALID_ID;

    private boolean isFabsShowing = false;

    private Toast toast;

    protected boolean isPaused = true;

    protected ContentMainBasePresenter presenter;

    protected boolean isFre = true;

    protected SearchCursorAdapter searchAdapter;

    protected abstract void suggestionClicked(int position);

    private OnLikeListener likeListener = new OnLikeListener() {
        @Override
        public void liked(LikeButton likeButton) {
            switch (likeButton.getId()) {
                case R.id.btn_fav:
                    presenter.favorited(getCurrentIndex(), true);
                    logUXEvent(FIRE_FAVORITE_ON);
                    break;
                case R.id.btn_thumb:
                    presenter.liked(getCurrentIndex());
                    logUXEvent(FIRE_THUMB_UP);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void unLiked(LikeButton likeButton) {
            switch (likeButton.getId()) {
                case R.id.btn_fav:
                    presenter.favorited(getCurrentIndex(), false);
                    logUXEvent(FIRE_FAVORITE_OFF);
                    break;
                default:
                    break;
            }
        }
    };

    private NumberPickerDialogFragment.INumberPickerDialogListener pickerListener =
            new NumberPickerDialogFragment.INumberPickerDialogListener() {
                @Override
                public void onPositiveClick(int number) {
                    scrollViewPagerToItem(number - 1, false);
                }

                @Override
                public void onNegativeClick() {
                    // Do nothing
                }
            };


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        setHasOptionsMenu(true);
        sd = new ShakeDetector(this);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        btnFav.setOnLikeListener(likeListener);
        btnThumb.setOnLikeListener(likeListener);

        viewPager.setAdapter(adapter);
        presenter.loadLatest();
        final ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getTitleTextRes());
        }
        if (savedInstanceState != null) {
            if (actionBar != null) {
                actionBar.setSubtitle(String.valueOf(lastViewdId));
            }
            NumberPickerDialogFragment pickerDialog =
                    (NumberPickerDialogFragment) getChildFragmentManager().findFragmentByTag("IdPickerDialogFragment");
            if (pickerDialog != null) {
                pickerDialog.setListener(pickerListener);
            }
        } else {
            Intent intent = getActivity().getIntent();
            if (intent != null) {
                int notiIndex = intent.getIntExtra(XKCD_INDEX_ON_NOTI_INTENT, INVALID_ID);

                if (notiIndex != INVALID_ID) {
                    lastViewdId = notiIndex;
                    latestIndex = lastViewdId;
                    presenter.setLatest(latestIndex);
                    Map<String, String> params = new HashMap<>();
                    params.put(FIRE_FROM_NOTIFICATION_INDEX, String.valueOf(notiIndex));
                    logUXEvent(FIRE_FROM_NOTIFICATION, params);
                }
                getActivity().setIntent(null);
            }
        }
        latestIndex = presenter.getLatest();
        lastViewdId = presenter.getLastViewed(latestIndex);
        isFre = latestIndex == INVALID_ID;
        if (latestIndex > INVALID_ID) {
            adapter.setSize(latestIndex);
            scrollViewPagerToItem(lastViewdId > INVALID_ID ? lastViewdId - 1 : latestIndex - 1, false);
        }
        SensorManager sensorManager = (SensorManager) getActivity().getSystemService(SENSOR_SERVICE);

        sd.start(sensorManager);

        return view;
    }

    protected abstract String getTitleTextRes();

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewPager != null && viewPager.getCurrentItem() >= 0) {
            outState.putInt(LAST_VIEW_XKCD_ID, viewPager.getCurrentItem() + 1);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isPaused = false;
    }

    @Override
    public void onPause() {
        isPaused = true;
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        sd.stop();
        presenter.onDestroy();
        super.onDestroyView();
    }

    @Override
    public void onStop() {
        if (viewPager != null && latestIndex > INVALID_ID) {
            int lastViewed = viewPager.getCurrentItem() + 1;
            presenter.setLastViewed(lastViewed);
        }
        super.onStop();
    }


    @SuppressLint("ObjectAnimatorBinding")
    protected void fabAnimation(@ColorRes final int startColor, @ColorRes final int endColor, @DrawableRes final int icon) {
        final ObjectAnimator animator = ObjectAnimator.ofInt(fab, "backgroundTint", getResources().getColor(startColor), getResources().getColor(endColor));
        animator.setDuration(1800L);
        animator.setEvaluator(new ArgbEvaluator());
        animator.setInterpolator(new DecelerateInterpolator(2));
        animator.addUpdateListener(animation -> {
            int animatedValue = (int) animation.getAnimatedValue();
            if (fab != null) {
                fab.setBackgroundTintList(ColorStateList.valueOf(animatedValue));
            }
        });
        animator.start();
        fab.setImageResource(icon);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_xkcd, menu);

        MenuItem itemRight = menu.findItem(R.id.action_right);
        ImageButton imageButtonRight = new ImageButton(getContext());
        imageButtonRight.setImageResource(R.drawable.ic_action_right);
        imageButtonRight.setBackground(null);

        itemRight.setActionView(imageButtonRight);
        imageButtonRight.setOnLongClickListener(v -> {
            scrollViewPagerToItem(latestIndex - 1, true);
            logUXEvent(FIRE_NEXT_BAR);
            return true;
        });
        imageButtonRight.setOnClickListener(v -> {
            String skipCount = getString(getResources().getIdentifier(sharedPreferences.getString(PREF_ARROW, "arrow_1"), "string", getActivity().getPackageName()));
            int skip = Integer.parseInt(skipCount);
            if (skip == 1) {
                scrollViewPagerToItem(viewPager.getCurrentItem() + skip, true);
            } else {
                scrollViewPagerToItem(viewPager.getCurrentItem() + skip, false);
            }
            logUXEvent(FIRE_NEXT_BAR);
        });

        MenuItem itemLeft = menu.findItem(R.id.action_left);
        ImageButton imageButtonLeft = new ImageButton(getContext());
        imageButtonLeft.setImageResource(R.drawable.ic_action_left);
        imageButtonLeft.setBackground(null);

        itemLeft.setActionView(imageButtonLeft);
        imageButtonLeft.setOnLongClickListener(v -> {
            scrollViewPagerToItem(0, true);
            logUXEvent(FIRE_PREVIOUS_BAR);
            return true;
        });
        imageButtonLeft.setOnClickListener(v -> {
            String skipCount = getString(getResources().getIdentifier(sharedPreferences.getString(PREF_ARROW, "arrow_1"), "string", getActivity().getPackageName()));
            int skip = Integer.parseInt(skipCount);
            if (skip == 1) {
                scrollViewPagerToItem(viewPager.getCurrentItem() - skip, true);
            } else {
                scrollViewPagerToItem(viewPager.getCurrentItem() - skip, false);
            }
            logUXEvent(FIRE_PREVIOUS_BAR);
        });
        setupSearch(menu);

    }


    @OnClick(R.id.fab)
    public void OnFABClicked() {
        toggleSubFabs(!isFabsShowing);
    }

    @OnPageChange(value = R.id.viewpager, callback = PAGE_SELECTED)
    public void OnPagerSelected(int position) {
        final ActionBar actionBar = ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(String.valueOf(position + 1));
        }
    }

    @OnPageChange(value = R.id.viewpager, callback = PAGE_SCROLL_STATE_CHANGED)
    public void onPageScrollStateChanged(int state) {
        if (state == SCROLL_STATE_DRAGGING) {
            fab.hide();
            toggleSubFabs(false);
        } else if (state == SCROLL_STATE_IDLE) {
            presenter.getInfoAndShowFab(getCurrentIndex());
        }
    }

    @Override
    public void hearShake() {
        if (isPaused) {
            return;
        }
        latestIndex = presenter.getLatest();
        if (latestIndex != INVALID_ID) {
            int randomId = new Random().nextInt(latestIndex + 1);
            scrollViewPagerToItem(randomId - 1, false);
        }
        getActivity().getWindow().getDecorView().performHapticFeedback(CONTEXT_CLICK, FLAG_IGNORE_GLOBAL_SETTING);
        logUXEvent(FIRE_SHAKE);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_search:
                logUXEvent(FIRE_SEARCH);
                break;
            case R.id.action_xkcd_list:
                Intent intent = new Intent(getActivity(), XkcdListActivity.class);
                startActivityForResult(intent, REQ_LIST_ACTIVITY);
                logUXEvent(FIRE_BROWSE_LIST_MENU);
                break;
            case R.id.action_specific:
                if (latestIndex == INVALID_ID) {
                    break;
                }
                NumberPickerDialogFragment pickerDialogFragment = new NumberPickerDialogFragment();
                pickerDialogFragment.setNumberRange(1, latestIndex);
                pickerDialogFragment.setListener(pickerListener);
                pickerDialogFragment.show(getChildFragmentManager(), "IdPickerDialogFragment");
                logUXEvent(FIRE_SPECIFIC_MENU);
                break;
            default:
                break;
        }
        return false;
    }

    protected void scrollViewPagerToItem(int id, boolean smoothScroll) {
        viewPager.setCurrentItem(id, smoothScroll);
        fab.hide();
        toggleSubFabs(false);
        if (!smoothScroll) {
            presenter.getInfoAndShowFab(getCurrentIndex());
        }
    }


    protected void toggleSubFabs(final boolean showSubFabs) {
        btnThumb.setClickable(showSubFabs);
        btnFav.setClickable(showSubFabs);
        ObjectAnimator thumbMove, thumbAlpha, favMove, favAlpha;
        if (showSubFabs) {
            thumbMove = ObjectAnimator.ofFloat(btnThumb, View.TRANSLATION_X, -215);
            thumbAlpha = ObjectAnimator.ofFloat(btnThumb, View.ALPHA, 1);
            favMove = ObjectAnimator.ofFloat(btnFav, View.TRANSLATION_X, -150, -400);
            favAlpha = ObjectAnimator.ofFloat(btnFav, View.ALPHA, 1);
        } else {
            thumbMove = ObjectAnimator.ofFloat(btnThumb, View.TRANSLATION_X, 0);
            thumbAlpha = ObjectAnimator.ofFloat(btnThumb, View.ALPHA, 0);
            favMove = ObjectAnimator.ofFloat(btnFav, View.TRANSLATION_X, -150);
            favAlpha = ObjectAnimator.ofFloat(btnFav, View.ALPHA, 0);
        }

        isFabsShowing = showSubFabs;
        AnimatorSet animSet = new AnimatorSet();
        animSet.playTogether(thumbMove, thumbAlpha, favMove, favAlpha);
        animSet.setDuration(300);
        animSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animator) {
                if (btnThumb != null && btnFav != null && showSubFabs) {
                    btnThumb.setVisibility(View.VISIBLE);
                    btnFav.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                if (btnThumb != null && btnFav != null && !showSubFabs) {
                    btnThumb.setVisibility(View.GONE);
                    btnFav.setVisibility(View.GONE);
                }
            }
        });
        animSet.start();
    }

    protected void showToast(Context context, String text) {
        try {
            toast.getView().isShown();
            toast.setText(text);
        } catch (Exception e) {
            toast = Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT);
        }
        toast.show();
    }


    protected int getCurrentIndex() {
        return viewPager.getCurrentItem() + 1;
    }

    protected void latestLoaded() {
        adapter.setSize(latestIndex);
        if (isFre) {
            scrollViewPagerToItem(latestIndex - 1, false);
        }
        presenter.setLatest(latestIndex);
    }

    private void setupSearch(Menu menu) {
        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView == null) {
            return;
        }
        searchView.setQueryHint(getSearchHint());
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        if (searchAdapter == null) {
            searchAdapter = new SearchCursorAdapter(getActivity(), null, 0);
        }
        searchView.setSuggestionsAdapter(searchAdapter);
        searchView.setOnSuggestionListener(new SearchView.OnSuggestionListener() {
            @Override
            public boolean onSuggestionSelect(int position) {
                return false;
            }

            @Override
            public boolean onSuggestionClick(int position) {
                suggestionClicked(position);
                searchView.clearFocus();
                searchItem.collapseActionView();
                return true;
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (TextUtils.isEmpty(newText)) {
                    return true;
                }
                presenter.searchContent(newText);
                return true;
            }
        });
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                setItemsVisibility(menu, new int[]{R.id.action_left, R.id.action_right, R.id.action_share}, false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                setItemsVisibility(menu, new int[]{R.id.action_left, R.id.action_right, R.id.action_share}, true);
                return true;
            }
        });
    }

    protected abstract CharSequence getSearchHint();

    private void setItemsVisibility(Menu menu, int[] hideItems, boolean visible) {
        for (int hideItem : hideItems) {
            menu.findItem(hideItem).setVisible(visible);
        }
    }

}

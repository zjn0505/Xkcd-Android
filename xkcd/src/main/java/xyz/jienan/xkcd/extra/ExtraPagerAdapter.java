package xyz.jienan.xkcd.extra;

import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import xyz.jienan.xkcd.extra.fragment.SingleExtraFragment;
import xyz.jienan.xkcd.extra.fragment.SingleExtraWebViewFragment;
import xyz.jienan.xkcd.home.base.BaseStatePagerAdapter;
import xyz.jienan.xkcd.model.ExtraComics;

public class ExtraPagerAdapter extends BaseStatePagerAdapter {

    private List<ExtraComics> extraComicsList;

    public ExtraPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        ExtraComics extraComics = extraComicsList.get(position);
        if (extraComics.links != null && !extraComics.links.isEmpty()) {
            SingleExtraWebViewFragment fragment = SingleExtraWebViewFragment.newInstance(extraComics);
            fragmentsMap.put(position + 1, fragment);
            return fragment;
        }
        SingleExtraFragment fragment = SingleExtraFragment.newInstance(position + 1);
        fragmentsMap.put(position + 1, fragment);
        return fragment;
    }

    public void setEntities(List<ExtraComics> extraComics) {
        this.extraComicsList = extraComics;
    }
}

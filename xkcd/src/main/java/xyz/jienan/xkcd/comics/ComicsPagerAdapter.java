package xyz.jienan.xkcd.comics;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import xyz.jienan.xkcd.comics.fragment.SingleComicFragment;
import xyz.jienan.xkcd.home.base.BaseStatePagerAdapter;

public class ComicsPagerAdapter extends BaseStatePagerAdapter {

    public ComicsPagerAdapter(FragmentManager fm) {
        super(fm);
    }

    @Override
    public Fragment getItem(int position) {
        SingleComicFragment fragment = SingleComicFragment.newInstance(position + 1);
        fragmentsMap.put(position + 1, fragment);
        return fragment;
    }
}

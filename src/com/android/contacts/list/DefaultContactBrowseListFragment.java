/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.contacts.list;

import com.android.contacts.R;
import com.android.contacts.ui.ContactsPreferencesActivity.Prefs;

import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment containing a contact list used for browsing (as compared to
 * picking a contact with one of the PICK intents).
 */
public class DefaultContactBrowseListFragment extends ContactBrowseListFragment
        implements OnItemSelectedListener {

    private static final String KEY_EDIT_MODE = "editMode";
    private static final String KEY_CREATE_CONTACT_ENABLED = "createContactEnabled";
    private static final String KEY_DISPLAY_WITH_PHONES_ONLY = "displayWithPhonesOnly";
    private static final String KEY_VISIBLE_CONTACTS_RESTRICTION = "visibleContactsRestriction";

    private boolean mEditMode;
    private boolean mCreateContactEnabled;
    private int mDisplayWithPhonesOnlyOption = ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED;
    private boolean mVisibleContactsRestrictionEnabled = true;
    private View mHeaderView;

    private boolean mFilterEnabled = true;
    private SparseArray<ContactListFilter> mFilters;
    private ArrayList<ContactListFilter> mFilterList;
    private int mNextFilterId = 1;
    private Spinner mFilterSpinner;
    private ContactListFilter mFilter;
    private boolean mFiltersLoaded;

    private LoaderCallbacks<List<ContactListFilter>> mGroupFilterLoaderCallbacks =
            new LoaderCallbacks<List<ContactListFilter>>() {

        @Override
        public ContactGroupFilterLoader onCreateLoader(int id, Bundle args) {
            return new ContactGroupFilterLoader(getContext());
        }

        @Override
        public void onLoadFinished(
                Loader<List<ContactListFilter>> loader, List<ContactListFilter> data) {
            onGroupFilterLoadFinished(data);
        }
    };

    public DefaultContactBrowseListFragment() {
        setPhotoLoaderEnabled(true);
        setSectionHeaderDisplayEnabled(true);
        setAizyEnabled(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_EDIT_MODE, mEditMode);
        outState.putBoolean(KEY_CREATE_CONTACT_ENABLED, mCreateContactEnabled);
        outState.putInt(KEY_DISPLAY_WITH_PHONES_ONLY, mDisplayWithPhonesOnlyOption);
        outState.putBoolean(KEY_VISIBLE_CONTACTS_RESTRICTION, mVisibleContactsRestrictionEnabled);
    }

    @Override
    public void restoreSavedState(Bundle savedState) {
        super.restoreSavedState(savedState);

        if (savedState == null) {
            return;
        }

        mEditMode = savedState.getBoolean(KEY_EDIT_MODE);
        mCreateContactEnabled = savedState.getBoolean(KEY_CREATE_CONTACT_ENABLED);
        mDisplayWithPhonesOnlyOption = savedState.getInt(KEY_DISPLAY_WITH_PHONES_ONLY);
        mVisibleContactsRestrictionEnabled =
                savedState.getBoolean(KEY_VISIBLE_CONTACTS_RESTRICTION);
    }

    @Override
    protected void prepareEmptyView() {
        if (isShowingContactsWithPhonesOnly()) {
            setEmptyText(R.string.noContactsWithPhoneNumbers);
        } else {
            super.prepareEmptyView();
        }
    }

    private boolean isShowingContactsWithPhonesOnly() {
        switch (mDisplayWithPhonesOnlyOption) {
            case ContactsRequest.DISPLAY_ONLY_WITH_PHONES_DISABLED:
                return false;
            case ContactsRequest.DISPLAY_ONLY_WITH_PHONES_ENABLED:
                return true;
            case ContactsRequest.DISPLAY_ONLY_WITH_PHONES_PREFERENCE:
                SharedPreferences prefs = PreferenceManager
                        .getDefaultSharedPreferences(getContext());
                return prefs.getBoolean(Prefs.DISPLAY_ONLY_PHONES,
                        Prefs.DISPLAY_ONLY_PHONES_DEFAULT);
        }
        return false;
    }

    public void setDisplayWithPhonesOnlyOption(int displayWithPhonesOnly) {
        mDisplayWithPhonesOnlyOption = displayWithPhonesOnly;
        configureAdapter();
    }

    public void setVisibleContactsRestrictionEnabled(boolean flag) {
        mVisibleContactsRestrictionEnabled = flag;
        configureAdapter();
    }

    @Override
    protected void onItemClick(int position, long id) {
        ContactListAdapter adapter = getAdapter();
        if (isEditMode()) {
            if (position == 0 && !isSearchMode() && isCreateContactEnabled()) {
                createNewContact();
            } else {
                editContact(adapter.getContactUri(position));
            }
        } else {
            viewContact(adapter.getContactUri(position), false);
        }
    }

    @Override
    protected ContactListAdapter createListAdapter() {
        DefaultContactListAdapter adapter = new DefaultContactListAdapter(getContext());
        adapter.setSectionHeaderDisplayEnabled(isSectionHeaderDisplayEnabled());
        adapter.setDisplayPhotos(true);
        return adapter;
    }

    @Override
    protected void configureAdapter() {
        super.configureAdapter();

        DefaultContactListAdapter adapter = (DefaultContactListAdapter)getAdapter();
        if (adapter != null) {
            adapter.setContactsWithPhoneNumbersOnly(isShowingContactsWithPhonesOnly());
            adapter.setVisibleContactsOnly(mVisibleContactsRestrictionEnabled);
            adapter.setFilter(mFilter, mFilterList);
        }
    }

    @Override
    protected View inflateView(LayoutInflater inflater, ViewGroup container) {
        return inflater.inflate(R.layout.contacts_list_content, null);
    }

    @Override
    protected void onCreateView(LayoutInflater inflater, ViewGroup container) {
        super.onCreateView(inflater, container);

        // Putting the header view inside a container will allow us to make
        // it invisible later. See checkHeaderViewVisibility()
        FrameLayout headerContainer = new FrameLayout(inflater.getContext());
        mHeaderView = inflater.inflate(R.layout.total_contacts, null, false);
        headerContainer.addView(mHeaderView);
        getListView().addHeaderView(headerContainer);
        checkHeaderViewVisibility();
        configureFilterSpinner();
    }

    protected void configureFilterSpinner() {
        mFilterSpinner = (Spinner)getView().findViewById(R.id.filter_spinner);
        if (mFilterSpinner == null) {
            return;
        }

        if (!mFilterEnabled) {
            mFilterSpinner.setVisibility(View.GONE);
            return;
        }
        mFilterSpinner.setOnItemSelectedListener(this);
    }

    @Override
    public void setSearchMode(boolean flag) {
        super.setSearchMode(flag);
        checkHeaderViewVisibility();
    }

    private void checkHeaderViewVisibility() {
        if (mHeaderView != null) {
            mHeaderView.setVisibility(isSearchMode() ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void showCount(int partitionIndex, Cursor data) {
        if (!isSearchMode() && data != null) {
            int count = data.getCount();
            // TODO
            // if (contactsListActivity.mDisplayOnlyPhones) {
            // text = contactsListActivity.getQuantityText(count,
            // R.string.listTotalPhoneContactsZero,
            // R.plurals.listTotalPhoneContacts);
            TextView textView = (TextView)getView().findViewById(R.id.totalContactsText);
            String text = getQuantityText(count, R.string.listTotalAllContactsZero,
                    R.plurals.listTotalAllContacts);
            textView.setText(text);
        }
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setEditMode(boolean flag) {
        mEditMode = flag;
    }

    public boolean isCreateContactEnabled() {
        return mCreateContactEnabled;
    }

    public void setCreateContactEnabled(boolean flag) {
        this.mCreateContactEnabled = flag;
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        setContactListFilter((int) id);
    }

    public void onNothingSelected(AdapterView<?> parent) {
        setContactListFilter(0);
    }

    @Override
    public void onStart() {
        if (mFilterEnabled) {
            mFiltersLoaded = false;
        }
        super.onStart();
    }

    @Override
    protected void startLoading() {
        // We need to load filters before we can load the list contents
        if (mFilterEnabled && !mFiltersLoaded) {
            getLoaderManager().restartLoader(
                    R.id.contact_list_filter_loader, null, mGroupFilterLoaderCallbacks);
        } else {
            super.startLoading();
        }
    }

    protected void onGroupFilterLoadFinished(List<ContactListFilter> filters) {
        if (mFilters == null) {
            mFilters = new SparseArray<ContactListFilter>(filters.size());
            mFilterList = new ArrayList<ContactListFilter>();
        } else {
            mFilters.clear();
            mFilterList.clear();
        }

        boolean filterValid = mFilter != null
                && (mFilter.filterType == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS
                        || mFilter.filterType == ContactListFilter.FILTER_TYPE_CUSTOM);

        int accountCount = 0;
        int count = filters.size();
        for (int index = 0; index < count; index++) {
            if (filters.get(index).filterType == ContactListFilter.FILTER_TYPE_ACCOUNT) {
                accountCount++;
            }
        }

        if (accountCount > 1) {
            mFilters.append(mNextFilterId++,
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS));
            mFilters.append(mNextFilterId++,
                    new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM));
        }

        boolean firstAccount = true;
        for (int index = 0; index < count; index++) {
            ContactListFilter filter = filters.get(index);
            mFilters.append(mNextFilterId++, filter);
            mFilterList.add(filter);
            filterValid |= filter.equals(mFilter);

            if (firstAccount && filter.filterType == ContactListFilter.FILTER_TYPE_ACCOUNT
                    && accountCount == 1) {
                firstAccount = false;
                mFilters.append(mNextFilterId++,
                        new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM));
            }
        }

        mFiltersLoaded = true;
        if (mFilter == null  || !filterValid) {
            mFilter = getDefaultFilter();
        }

        mFilterSpinner.setAdapter(new FilterSpinnerAdapter());
        updateFilterView();

        startLoading();
    }

    protected void setContactListFilter(int filterId) {
        ContactListFilter filter;
        if (filterId == ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS);
        } else if (filterId == ContactListFilter.FILTER_TYPE_CUSTOM) {
            filter = new ContactListFilter(ContactListFilter.FILTER_TYPE_CUSTOM);
        } else {
            filter = mFilters.get(filterId);
            if (filter == null) {
                filter = getDefaultFilter();
            }
        }

        if (!filter.equals(mFilter)) {
            mFilter = filter;
            updateFilterView();
            reloadData();
        }
    }

    private ContactListFilter getDefaultFilter() {
        return mFilters.valueAt(0);
    }

    protected void updateFilterView() {
        if (mFiltersLoaded) {
            mFilterSpinner.setVisibility(View.VISIBLE);
        }
    }

    private class FilterSpinnerAdapter extends BaseAdapter {
        private LayoutInflater mLayoutInflater;

        public FilterSpinnerAdapter() {
            mLayoutInflater = LayoutInflater.from(getContext());
        }

        @Override
        public int getCount() {
            return mFilters.size();
        }

        @Override
        public long getItemId(int position) {
            return mFilters.keyAt(position);
        }

        @Override
        public Object getItem(int position) {
            return mFilters.valueAt(position);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent, true);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getView(position, convertView, parent, false);
        }

        public View getView(int position, View convertView, ViewGroup parent, boolean dropdown) {
            View view = convertView != null ? convertView
                    : mLayoutInflater.inflate(R.layout.filter_spinner_item, parent, false);
            ImageView icon = (ImageView) view.findViewById(R.id.icon);
            TextView label = (TextView) view.findViewById(R.id.label);
            TextView indentedLabel = (TextView) view.findViewById(R.id.indented_label);
            ContactListFilter filter = mFilters.valueAt(position);
            switch (filter.filterType) {
                case ContactListFilter.FILTER_TYPE_ALL_ACCOUNTS: {
                    icon.setVisibility(View.GONE);
                    label.setText(R.string.list_filter_all_accounts);
                    label.setVisibility(View.VISIBLE);
                    indentedLabel.setVisibility(View.GONE);
                    break;
                }
                case ContactListFilter.FILTER_TYPE_CUSTOM: {
                    icon.setVisibility(View.GONE);
                    label.setText(dropdown
                            ? R.string.list_filter_customize
                            : R.string.list_filter_custom);
                    label.setVisibility(View.VISIBLE);
                    indentedLabel.setVisibility(View.GONE);
                    break;
                }
                case ContactListFilter.FILTER_TYPE_ACCOUNT: {
                    icon.setVisibility(View.VISIBLE);
                    if (filter.icon != null) {
                        icon.setImageDrawable(filter.icon);
                    } else {
                        icon.setImageResource(R.drawable.unknown_source);
                    }
                    label.setText(filter.accountName);
                    label.setVisibility(View.VISIBLE);
                    indentedLabel.setVisibility(View.GONE);
                    break;
                }
                case ContactListFilter.FILTER_TYPE_GROUP: {
                    icon.setVisibility(View.GONE);
                    if (dropdown) {
                        label.setVisibility(View.GONE);
                        indentedLabel.setText(filter.title);
                        indentedLabel.setVisibility(View.VISIBLE);
                    } else {
                        label.setText(filter.title);
                        label.setVisibility(View.VISIBLE);
                        indentedLabel.setVisibility(View.GONE);
                    }
                    break;
                }
            }
            return view;
        }
    }
}

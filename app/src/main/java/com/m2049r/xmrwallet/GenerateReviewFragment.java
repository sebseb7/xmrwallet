/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.m2049r.xmrwallet;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.m2049r.xmrwallet.model.NetworkType;
import com.m2049r.xmrwallet.util.KeyStoreHelper;
import com.m2049r.xmrwallet.widget.Toolbar;
import com.m2049r.xmrwallet.model.Wallet;
import com.m2049r.xmrwallet.model.WalletManager;
import com.m2049r.xmrwallet.util.Helper;
import com.m2049r.xmrwallet.util.MoneroThreadPoolExecutor;

import java.io.File;

import timber.log.Timber;

public class GenerateReviewFragment extends Fragment {
    static final public String VIEW_TYPE_DETAILS = "details";
    static final public String VIEW_TYPE_ACCEPT = "accept";
    static final public String VIEW_TYPE_WALLET = "wallet";

    ScrollView scrollview;

    ProgressBar pbProgress;
    TextView tvWalletPassword;
    TextView tvWalletAddress;
    TextView tvWalletMnemonic;
    TextView tvWalletViewKey;
    TextView tvWalletSpendKey;
    ImageButton bCopyAddress;
    LinearLayout llAdvancedInfo;
    LinearLayout llPassword;
    Button bAdvancedInfo;
    Button bAccept;

    // TODO fix visibility of variables
    String walletPath;
    String walletName;
    // we need to keep the password so the user is not asked again if they want to change it
    // note they can only enter this fragment immediately after entering the password
    // so asking them to enter it a couple of seconds later seems silly
    String walletPassword = null;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_review, container, false);

        scrollview = (ScrollView) view.findViewById(R.id.scrollview);
        pbProgress = (ProgressBar) view.findViewById(R.id.pbProgress);
        tvWalletPassword = (TextView) view.findViewById(R.id.tvWalletPassword);
        tvWalletAddress = (TextView) view.findViewById(R.id.tvWalletAddress);
        tvWalletViewKey = (TextView) view.findViewById(R.id.tvWalletViewKey);
        tvWalletSpendKey = (TextView) view.findViewById(R.id.tvWalletSpendKey);
        tvWalletMnemonic = (TextView) view.findViewById(R.id.tvWalletMnemonic);
        bCopyAddress = (ImageButton) view.findViewById(R.id.bCopyAddress);
        bAdvancedInfo = (Button) view.findViewById(R.id.bAdvancedInfo);
        llAdvancedInfo = (LinearLayout) view.findViewById(R.id.llAdvancedInfo);
        llPassword = (LinearLayout) view.findViewById(R.id.llPassword);

        bAccept = (Button) view.findViewById(R.id.bAccept);

        boolean testnet = WalletManager.getInstance().getNetworkType() != NetworkType.NetworkType_Mainnet;
        tvWalletMnemonic.setTextIsSelectable(testnet);
        tvWalletSpendKey.setTextIsSelectable(testnet);
        tvWalletPassword.setTextIsSelectable(testnet);

        bAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptWallet();
            }
        });
        view.findViewById(R.id.bCopyViewKey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyViewKey();
            }
        });
        bCopyAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyAddress();
            }
        });
        bCopyAddress.setClickable(false);
        view.findViewById(R.id.bAdvancedInfo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdvancedInfo();
            }
        });

        Bundle args = getArguments();
        type = args.getString("type");
        walletPath = args.getString("path");
        showDetails(args.getString("password"));
        return view;
    }

    void showDetails(String password) {
        walletPassword = password;
        showProgress();
        tvWalletPassword.setText(null);
        new AsyncShow().executeOnExecutor(MoneroThreadPoolExecutor.MONERO_THREAD_POOL_EXECUTOR, walletPath);
    }

    void copyViewKey() {
        Helper.clipBoardCopy(getActivity(), getString(R.string.label_copy_viewkey), tvWalletViewKey.getText().toString());
        Toast.makeText(getActivity(), getString(R.string.message_copy_viewkey), Toast.LENGTH_SHORT).show();
    }

    void copyAddress() {
        Helper.clipBoardCopy(getActivity(), getString(R.string.label_copy_address), tvWalletAddress.getText().toString());
        Toast.makeText(getActivity(), getString(R.string.message_copy_address), Toast.LENGTH_SHORT).show();
    }

    void nocopy() {
        Toast.makeText(getActivity(), getString(R.string.message_nocopy), Toast.LENGTH_SHORT).show();
    }

    void showAdvancedInfo() {
        llAdvancedInfo.setVisibility(View.VISIBLE);
        bAdvancedInfo.setVisibility(View.GONE);
        scrollview.post(new Runnable() {
            @Override
            public void run() {
                scrollview.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    String type;

    private void acceptWallet() {
        bAccept.setEnabled(false);
        acceptCallback.onAccept(walletName, walletPassword);
    }

    private class AsyncShow extends AsyncTask<String, Void, Boolean> {
        String name;
        String address;
        String seed;
        String viewKey;
        String spendKey;
        boolean isWatchOnly;
        Wallet.Status status;

        @Override
        protected Boolean doInBackground(String... params) {
            if (params.length != 1) return false;
            String walletPath = params[0];

            Wallet wallet;
            boolean closeWallet;
            if (type.equals(GenerateReviewFragment.VIEW_TYPE_WALLET)) {
                wallet = GenerateReviewFragment.this.walletCallback.getWallet();
                closeWallet = false;
            } else {
                wallet = WalletManager.getInstance().openWallet(walletPath, walletPassword);
                closeWallet = true;
            }
            name = wallet.getName();
            status = wallet.getStatus();
            if (status != Wallet.Status.Status_Ok) {
                Timber.e(wallet.getErrorString());
                if (closeWallet) wallet.close();
                return false;
            }

            address = wallet.getAddress();
            seed = wallet.getSeed();
            viewKey = wallet.getSecretViewKey();
            spendKey = isWatchOnly ? getActivity().getString(R.string.label_watchonly) : wallet.getSecretSpendKey();
            isWatchOnly = wallet.isWatchOnly();
            if (closeWallet) wallet.close();
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (!isAdded()) return; // never mind
            walletName = name;
            if (result) {
                if (type.equals(GenerateReviewFragment.VIEW_TYPE_ACCEPT)) {
                    bAccept.setVisibility(View.VISIBLE);
                    bAccept.setEnabled(true);
                }
                if (walletPassword != null) {
                    llPassword.setVisibility(View.VISIBLE);
                    tvWalletPassword.setText(walletPassword);
                }
                tvWalletAddress.setText(address);
                tvWalletMnemonic.setText(seed);
                tvWalletViewKey.setText(viewKey);
                tvWalletSpendKey.setText(spendKey);
                bAdvancedInfo.setVisibility(View.VISIBLE);
                bCopyAddress.setClickable(true);
                bCopyAddress.setImageResource(R.drawable.ic_content_copy_black_24dp);
                activityCallback.setTitle(name, getString(R.string.details_title));
                activityCallback.setToolbarButton(
                        GenerateReviewFragment.VIEW_TYPE_ACCEPT.equals(type) ? Toolbar.BUTTON_NONE : Toolbar.BUTTON_BACK);
            } else {
                // TODO show proper error message and/or end the fragment?
                tvWalletAddress.setText(status.toString());
                tvWalletMnemonic.setText(status.toString());
                tvWalletViewKey.setText(status.toString());
                tvWalletSpendKey.setText(status.toString());
            }
            hideProgress();
        }
    }

    Listener activityCallback = null;
    ProgressListener progressCallback = null;
    AcceptListener acceptCallback = null;
    ListenerWithWallet walletCallback = null;

    public interface Listener {
        void setTitle(String title, String subtitle);

        void setToolbarButton(int type);
    }

    public interface ProgressListener {
        void showProgressDialog(int msgId);

        void dismissProgressDialog();
    }


    public interface AcceptListener {
        void onAccept(String name, String password);
    }

    public interface ListenerWithWallet {
        Wallet getWallet();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Listener) {
            this.activityCallback = (Listener) context;
        }
        if (context instanceof ProgressListener) {
            this.progressCallback = (ProgressListener) context;
        }
        if (context instanceof AcceptListener) {
            this.acceptCallback = (AcceptListener) context;
        }
        if (context instanceof ListenerWithWallet) {
            this.walletCallback = (ListenerWithWallet) context;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume()");
        activityCallback.setTitle(walletName, getString(R.string.details_title));
        activityCallback.setToolbarButton(
                GenerateReviewFragment.VIEW_TYPE_ACCEPT.equals(type) ? Toolbar.BUTTON_NONE : Toolbar.BUTTON_BACK);
    }

    public void showProgress() {
        pbProgress.setVisibility(View.VISIBLE);
    }

    public void hideProgress() {
        pbProgress.setVisibility(View.GONE);
    }

    boolean backOk() {
        return !type.equals(GenerateReviewFragment.VIEW_TYPE_ACCEPT);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        String type = getArguments().getString("type");
        if (GenerateReviewFragment.VIEW_TYPE_ACCEPT.equals(type)) {
            inflater.inflate(R.menu.wallet_details_help_menu, menu);
            super.onCreateOptionsMenu(menu, inflater);
        } else {
            inflater.inflate(R.menu.wallet_details_menu, menu);
            super.onCreateOptionsMenu(menu, inflater);
        }
    }

    boolean changeWalletPassword(String newPassword) {
        Wallet wallet;
        boolean closeWallet;
        if (type.equals(GenerateReviewFragment.VIEW_TYPE_WALLET)) {
            wallet = GenerateReviewFragment.this.walletCallback.getWallet();
            closeWallet = false;
        } else {
            wallet = WalletManager.getInstance().openWallet(walletPath, walletPassword);
            closeWallet = true;
        }

        boolean ok = false;
        if (wallet.getStatus() == Wallet.Status.Status_Ok) {
            wallet.setPassword(newPassword);
            wallet.store();
            ok = true;
        } else {
            Timber.e(wallet.getErrorString());
        }
        if (closeWallet) wallet.close();
        return ok;
    }

    private class AsyncChangePassword extends AsyncTask<String, Void, Boolean> {
        String newPassword;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (progressCallback != null)
                progressCallback.showProgressDialog(R.string.changepw_progress);
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (params.length != 3) return false;
            File walletFile = Helper.getWalletFile(getActivity(), params[0]);
            String oldPassword = params[1];
            String userPassword = params[2];
            newPassword = KeyStoreHelper.getCrazyPass(getActivity(), userPassword);
            return changeWalletPassword(newPassword);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (getActivity().isDestroyed()) {
                return;
            }
            if (progressCallback != null)
                progressCallback.dismissProgressDialog();
            if (result) {
                Toast.makeText(getActivity(), getString(R.string.changepw_success), Toast.LENGTH_SHORT).show();
                showDetails(newPassword);
            } else {
                Toast.makeText(getActivity(), getString(R.string.changepw_failed), Toast.LENGTH_LONG).show();
            }
        }
    }

    AlertDialog openDialog = null; // for preventing opening of multiple dialogs

    public AlertDialog createChangePasswordDialog() {
        if (openDialog != null) return null; // we are already open
        LayoutInflater li = LayoutInflater.from(getActivity());
        View promptsView = li.inflate(R.layout.prompt_changepw, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(getActivity());
        alertDialogBuilder.setView(promptsView);

        final TextInputLayout etPasswordA = (TextInputLayout) promptsView.findViewById(R.id.etWalletPasswordA);
        etPasswordA.setHint(getString(R.string.prompt_changepw, walletName));

        final TextInputLayout etPasswordB = (TextInputLayout) promptsView.findViewById(R.id.etWalletPasswordB);
        etPasswordB.setHint(getString(R.string.prompt_changepwB, walletName));

        etPasswordA.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (etPasswordA.getError() != null) {
                    etPasswordA.setError(null);
                }
                if (etPasswordB.getError() != null) {
                    etPasswordB.setError(null);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        etPasswordB.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                if (etPasswordA.getError() != null) {
                    etPasswordA.setError(null);
                }
                if (etPasswordB.getError() != null) {
                    etPasswordB.setError(null);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {
            }
        });

        // set dialog message
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton(getString(R.string.label_ok), null)
                .setNegativeButton(getString(R.string.label_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Helper.hideKeyboardAlways(getActivity());
                                dialog.cancel();
                                openDialog = null;
                            }
                        });

        openDialog = alertDialogBuilder.create();
        openDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(final DialogInterface dialog) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        String newPasswordA = etPasswordA.getEditText().getText().toString();
                        String newPasswordB = etPasswordB.getEditText().getText().toString();
                        // disallow empty passwords
                        if (newPasswordA.isEmpty()) {
                            etPasswordA.setError(getString(R.string.generate_empty_passwordB));
                        } else if (!newPasswordA.equals(newPasswordB)) {
                            etPasswordB.setError(getString(R.string.generate_bad_passwordB));
                        } else if (newPasswordA.equals(newPasswordB)) {
                            new AsyncChangePassword().execute(walletName, walletPassword, newPasswordA);
                            Helper.hideKeyboardAlways(getActivity());
                            openDialog.dismiss();
                            openDialog = null;
                        }
                    }
                });
            }
        });

        // accept keyboard "ok"
        etPasswordB.getEditText().setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                    String newPasswordA = etPasswordA.getEditText().getText().toString();
                    String newPasswordB = etPasswordB.getEditText().getText().toString();
                    // disallow empty passwords
                    if (newPasswordA.isEmpty()) {
                        etPasswordA.setError(getString(R.string.generate_empty_passwordB));
                    } else if (!newPasswordA.equals(newPasswordB)) {
                        etPasswordB.setError(getString(R.string.generate_bad_passwordB));
                    } else if (newPasswordA.equals(newPasswordB)) {
                        new AsyncChangePassword().execute(walletName, walletPassword, newPasswordA);
                        Helper.hideKeyboardAlways(getActivity());
                        openDialog.dismiss();
                        openDialog = null;
                    }
                    return true;
                }
                return false;
            }
        });
        return openDialog;
    }

}

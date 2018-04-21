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

package com.m2049r.xmrwallet.model;

import com.m2049r.xmrwallet.data.WalletNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

public class WalletManager {

    static {
        System.loadLibrary("monerujo");
    }

    // no need to keep a reference to the REAL WalletManager (we get it every tvTime we need it)
    private static WalletManager Instance = null;

    public static synchronized WalletManager getInstance() {
        if (WalletManager.Instance == null) {
            WalletManager.Instance = new WalletManager();
        }

        return WalletManager.Instance;
    }

    //private Map<String, Wallet> managedWallets;
    private Wallet managedWallet = null;

    public Wallet getWallet() {
        return managedWallet;
    }

    private void manageWallet(Wallet wallet) {
        Timber.d("Managing %s", wallet.getName());
        managedWallet = wallet;
    }

    private void unmanageWallet(Wallet wallet) {
        if (wallet == null) {
            throw new IllegalArgumentException("Cannot unmanage null!");
        }
        if (getWallet() == null) {
            throw new IllegalStateException("No wallet under management!");
        }
        if (getWallet() != wallet) {
            throw new IllegalStateException(wallet.getName() + " not under management!");
        }
        Timber.d("Unmanaging %s", managedWallet.getName());
        managedWallet = null;
    }

    public Wallet createWallet(File aFile, String password, String language) {
        long walletHandle = createWalletJ(aFile.getAbsolutePath(), password, language, getNetworkType().getValue());
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long createWalletJ(String path, String password, String language, int networkType);

    public Wallet openWallet(String path, String password) {
        long walletHandle = openWalletJ(path, password, getNetworkType().getValue());
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long openWalletJ(String path, String password, int networkType);

    public Wallet recoveryWallet(File aFile, String password, String mnemonic) {
        return recoveryWallet(aFile, password, mnemonic, 0);
    }

    public Wallet recoveryWallet(File aFile, String password, String mnemonic, long restoreHeight) {
        long walletHandle = recoveryWalletJ(aFile.getAbsolutePath(), password, mnemonic,
                getNetworkType().getValue(), restoreHeight);
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long recoveryWalletJ(String path, String password, String mnemonic,
                                        int networkType, long restoreHeight);

    public Wallet createWalletWithKeys(File aFile, String password, String language, long restoreHeight,
                                       String addressString, String viewKeyString, String spendKeyString) {
        long walletHandle = createWalletFromKeysJ(aFile.getAbsolutePath(), password,
                language, getNetworkType().getValue(), restoreHeight,
                addressString, viewKeyString, spendKeyString);
        Wallet wallet = new Wallet(walletHandle);
        manageWallet(wallet);
        return wallet;
    }

    private native long createWalletFromKeysJ(String path, String password,
                                              String language,
                                              int networkType,
                                              long restoreHeight,
                                              String addressString,
                                              String viewKeyString,
                                              String spendKeyString);

    public native boolean closeJ(Wallet wallet);

    public boolean close(Wallet wallet) {
        unmanageWallet(wallet);
        boolean closed = closeJ(wallet);
        if (!closed) {
            // in case we could not close it
            // we manage it again
            manageWallet(wallet);
        }
        return closed;
    }

    public boolean walletExists(File aFile) {
        return walletExists(aFile.getAbsolutePath());
    }

    public native boolean walletExists(String path);

    public native boolean verifyWalletPassword(String keys_file_name, String password, boolean watch_only);

    //public native List<String> findWallets(String path); // this does not work - some error in boost

    public class WalletInfo implements Comparable<WalletInfo> {
        public File path;
        public String name;
        public String address;

        @Override
        public int compareTo(WalletInfo another) {
            int n = name.toLowerCase().compareTo(another.name.toLowerCase());
            if (n != 0) {
                return n;
            } else { // wallet names are the same
                return address.compareTo(another.address);
            }
        }
    }

    public WalletInfo getWalletInfo(File wallet) {
        WalletInfo info = new WalletInfo();
        info.path = wallet.getParentFile();
        info.name = wallet.getName();
        File addressFile = new File(info.path, info.name + ".address.txt");
        //Timber.d(addressFile.getAbsolutePath());
        info.address = "??????";
        BufferedReader addressReader = null;
        try {
            addressReader = new BufferedReader(new FileReader(addressFile));
            info.address = addressReader.readLine();
        } catch (IOException ex) {
            Timber.d(ex.getLocalizedMessage());
        } finally {
            if (addressReader != null) {
                try {
                    addressReader.close();
                } catch (IOException ex) {
                    // that's just too bad
                }
            }
        }
        return info;
    }

    public List<WalletInfo> findWallets(File path) {
        List<WalletInfo> wallets = new ArrayList<>();
        Timber.d("Scanning: %s", path.getAbsolutePath());
        File[] found = path.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".keys");
            }
        });
        for (int i = 0; i < found.length; i++) {
            String filename = found[i].getName();
            File f = new File(found[i].getParent(), filename.substring(0, filename.length() - 5)); // 5 is length of ".keys"+1
            wallets.add(getWalletInfo(f));
        }
        return wallets;
    }

    public native String getErrorString();

//TODO virtual bool checkPayment(const std::string &address, const std::string &txid, const std::string &txkey, const std::string &daemon_address, uint64_t &received, uint64_t &height, std::string &error) const = 0;

    private String daemonAddress = null;
    private NetworkType networkType = null;

    public NetworkType getNetworkType() {
        return networkType;
    }

    //public void setDaemon(String address, NetworkType networkType, String username, String password) {
    public void setDaemon(WalletNode walletNode) {
        this.daemonAddress = walletNode.getAddress();
        this.networkType = walletNode.getNetworkType();
        this.daemonUsername = walletNode.getUsername();
        this.daemonPassword = walletNode.getPassword();
        setDaemonAddressJ(daemonAddress);
    }

    public void setNetworkType(NetworkType networkType) {
        this.networkType = networkType;
    }

    public String getDaemonAddress() {
        if (daemonAddress == null) {
            // assume testnet not explicitly initialised
            throw new IllegalStateException("use setDaemon() to initialise daemon and net first!");
        }
        return this.daemonAddress;
    }

    private native void setDaemonAddressJ(String address);

    String daemonUsername = "";

    public String getDaemonUsername() {
        return daemonUsername;
    }

    String daemonPassword = "";

    public String getDaemonPassword() {
        return daemonPassword;
    }

    public native int getDaemonVersion();

    public native long getBlockchainHeight();

    public native long getBlockchainTargetHeight();

    public native long getNetworkDifficulty();

    public native double getMiningHashRate();

    public native long getBlockTarget();

    public native boolean isMining();

    public native boolean startMining(String address, boolean background_mining, boolean ignore_battery);

    public native boolean stopMining();

    public native String resolveOpenAlias(String address, boolean dnssec_valid);

//TODO static std::tuple<bool, std::string, std::string, std::string, std::string> checkUpdates(const std::string &software, const std::string &subdir);

    static public native void initLogger(String argv0, String defaultLogBaseName);

    //TODO: maybe put these in an enum like in monero core - but why?
    static public int LOGLEVEL_SILENT = -1;
    static public int LOGLEVEL_WARN = 0;
    static public int LOGLEVEL_INFO = 1;
    static public int LOGLEVEL_DEBUG = 2;
    static public int LOGLEVEL_TRACE = 3;
    static public int LOGLEVEL_MAX = 4;

    static public native void setLogLevel(int level);

    static public native void logDebug(String category, String message);

    static public native void logInfo(String category, String message);

    static public native void logWarning(String category, String message);

    static public native void logError(String category, String message);
}
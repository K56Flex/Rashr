package de.mkrtchyan.recoverytools;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialog;
import android.support.v7.widget.AppCompatButton;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.fima.cardsui.objects.Card;
import com.fima.cardsui.views.CardUI;
import com.fima.cardsui.views.IconCard;
import com.fima.cardsui.views.SimpleCard;

import org.sufficientlysecure.rootcommands.Toolbox;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;

import de.mkrtchyan.utils.Common;
import de.mkrtchyan.utils.DownloadDialog;
import de.mkrtchyan.utils.Downloader;
import de.mkrtchyan.utils.FileChooserDialog;

/**
 * Copyright (c) 2016 Aschot Mkrtchyan
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
public class FlashFragment extends Fragment {

    private SwipeRefreshLayout mSwipeUpdater = null;
    private Context mContext;
    private RashrActivity mActivity;
    private boolean isRecoveryListUpToDate = true;
    private boolean isKernelListUpToDate = true;

    public FlashFragment() {
    }

    public static FlashFragment newInstance(RashrActivity activity) {
        FlashFragment fragment = new FlashFragment();
        fragment.setActivity(activity);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        /**
         * Backups menu only accessible if backups are possible
         */
        if (RashrApp.DEVICE.isRecoveryDD() || RashrApp.DEVICE.isKernelDD()
                || RashrApp.DEVICE.isRecoveryMTD() || RashrApp.DEVICE.isKernelMTD())
            inflater.inflate(R.menu.flash_menu, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @SuppressLint("PrivateResource")
    @SuppressWarnings("ResourceAsColor")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_rashr, container, false);
        /** Check if device uses unified builds */
        if (Common.getBooleanPref(mContext, Const.PREF_NAME, Const.PREF_KEY_SHOW_UNIFIED)
                && RashrApp.DEVICE.isUnified()
                && (!RashrApp.DEVICE.isStockRecoverySupported() || !RashrApp.DEVICE.isCwmRecoverySupported()
                || !RashrApp.DEVICE.isTwrpRecoverySupported() || !RashrApp.DEVICE.isPhilzRecoverySupported())) {
            showUnifiedBuildsDialog();
        }
        optimizeLayout(root);
        if (Common.getBooleanPref(mContext, Const.PREF_NAME, Const.PREF_KEY_CHECK_UPDATES)
                && RashrActivity.FirstSession) {
            catchUpdates();
            RashrActivity.FirstSession = false;
        }
        return root;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.BackupItem:
                mActivity.switchTo(BackupRestoreFragment.newInstance(mActivity), true);
                break;
        }
        return false;
    }

    /**
     * Flash Recovery from storage (already downloaded)
     */
    public void bFlashOtherRecovery(View view) {
        String AllowedEXT[] = {RashrApp.DEVICE.getRecoveryExt()};
        FileChooserDialog chooser = new FileChooserDialog(view.getContext());
        chooser.setAllowedEXT(AllowedEXT);
        chooser.setBrowseUpAllowed(true);
        chooser.setOnFileChooseListener(new FileChooserDialog.OnFileChooseListener() {
            @Override
            public void OnFileChoose(File file) {
                flashRecovery(file);
            }
        });
        chooser.setStartFolder(Const.PathToSd);
        chooser.setWarn(true);
        chooser.show();
    }

    /**
     * Flash Kernels provided by Rashr like stock kernels for Nexus Devices
     *
     * @param card CardView that contains the Kernel type should be flashed for example: stock,
     *             bricked...
     */
    public void FlashSupportedKernel(Card card) {
        final File path;
        final ArrayList<String> Versions;
        ArrayAdapter<String> VersionsAdapter = new ArrayAdapter<>(mContext, R.layout.custom_list_item);
        /**
         * If there files be needed to flash download it and listing device specified recovery
         * file for example stock-boot-grouper-4.4.img (read out from kernel_sums)
         */
        String SYSTEM = card.getData().toString();
        if (SYSTEM.equals(Device.KER_SYS_STOCK)) {
            Versions = RashrApp.DEVICE.getStockKernelVersions();
            path = Const.PathToStockKernel;
            for (String i : Versions) {
                try {
                    String version = i.split("-")[3].replace(RashrApp.DEVICE.getRecoveryExt(), "");
                    String deviceName = i.split("-")[2];
                    /** Readable name for user */
                    VersionsAdapter.add("Stock Kernel " + version + " (" + deviceName + ")");
                } catch (ArrayIndexOutOfBoundsException e) {
                    /** Add the normal filename if something went wrong */
                    VersionsAdapter.add(i);
                }
            }
        } else {
            /** Only stock kernel is supported at the moment, why are you here?
             * Something went wrong better return :)
             */
            return;
        }

        final AlertDialog.Builder KernelDialog = new AlertDialog.Builder(mContext);
        KernelDialog.setTitle(SYSTEM);
        KernelDialog.setAdapter(VersionsAdapter, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                final File kernel = new File(path, Versions.get(which));

                if (!kernel.exists()) {
                    try {
                        URL url = new URL(Const.KERNEL_URL + "/" + kernel.getName());
                        Downloader downloader = new Downloader(url, kernel);
                        final DownloadDialog KernelDownloader = new DownloadDialog(mContext, downloader);
                        KernelDownloader.setOnDownloadListener(new DownloadDialog.OnDownloadListener() {
                            @Override
                            public void onSuccess(File file) {
                                flashKernel(file);
                            }

                            @Override
                            public void onFail(Exception e) {
                                KernelDownloader.retry();
                            }
                        });
                        KernelDownloader.setAskBeforeDownload(true);
                        downloader.setChecksumFile(Const.KernelCollectionFile);
                        KernelDownloader.ask();
                    } catch (MalformedURLException ignored) {
                    }
                } else {
                    flashKernel(kernel);
                }
            }
        });
        KernelDialog.show();
        //}
    }

    /**
     * Flash Kernel from storage (separate downloaded)
     */
    public void bFlashOtherKernel(View view) {
        FileChooserDialog chooser = new FileChooserDialog(view.getContext());
        String AllowedEXT[] = {RashrApp.DEVICE.getKernelExt()};
        chooser.setOnFileChooseListener(new FileChooserDialog.OnFileChooseListener() {
            @Override
            public void OnFileChoose(File file) {
                flashKernel(file);
            }
        });
        chooser.setStartFolder(Const.PathToSd);
        chooser.setAllowedEXT(AllowedEXT);
        chooser.setBrowseUpAllowed(true);
        chooser.setWarn(true);
        chooser.show();
    }

    /**
     * Lists the last 5 flashed images and shows a dialog for a re-flash
     */
    public void showFlashHistory() {
        final ArrayList<File> HistoryFiles = getHistoryFiles();
        final ArrayList<String> HistoryFileNames = new ArrayList<>();
        final AlertDialog.Builder HistoryDialog = new AlertDialog.Builder(mContext);
        HistoryDialog.setTitle(R.string.history);

        for (File i : HistoryFiles) {
            HistoryFileNames.add(i.getName());
        }

        HistoryDialog.setAdapter(new ArrayAdapter<>(mContext, android.R.layout.simple_list_item_1,
                HistoryFileNames), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (HistoryFiles.get(which).exists()) {
                    mActivity.switchTo(FlashAsFragment.newInstance(mActivity,
                            HistoryFiles.get(which), true));
                }
            }
        });
        if (HistoryFileNames.toArray().length > 0) {
            HistoryDialog.show();
        } else {
            Toast
                    .makeText(mActivity, R.string.no_history, Toast.LENGTH_SHORT)
                    .show();
        }
    }

    /**
     * Flash recovery using FlashUtil
     *
     * @param recovery recovery image (appropriated for this device)
     */
    private void flashRecovery(final File recovery) {
        if (recovery != null) {
            if (recovery.exists() && recovery.getName().endsWith(RashrApp.DEVICE.getRecoveryExt())
                    && !recovery.isDirectory()) {
                if (!RashrApp.DEVICE.isFOTAFlashed() && !RashrApp.DEVICE.isRecoveryOverRecovery()) {
                    /** Flash not need to be handled specially */
                    final FlashUtil flashUtil = new FlashUtil(mActivity, recovery, FlashUtil.JOB_FLASH_RECOVERY);
                    flashUtil.setOnTaskDoneListener(new FlashUtil.OnTaskDoneListener() {
                        @Override
                        public void onSuccess() {

                        }

                        @Override
                        public void onFail(Exception e) {
                            RashrApp.ERRORS.add(e.toString());
                            AlertDialog.Builder d = new AlertDialog.Builder(mContext);
                            d.setTitle(R.string.flash_error);
                            d.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                }
                            });
                            if (e instanceof FlashUtil.ImageNotValidException) {
                                d.setMessage(getString(R.string.image_not_valid_message));
                                d.setNeutralButton(R.string.settings, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mActivity.switchTo(SettingsFragment.newInstance());
                                    }
                                });
                            } else if (e instanceof FlashUtil.ImageToBigException) {
                                d.setMessage(String.format(getString(R.string.image_to_big_message), ((FlashUtil.ImageToBigException) e).getCustomSize() / (1024 * 1024), ((FlashUtil.ImageToBigException) e).getPartitionSize() / (1024 * 1024)));
                                d.setNeutralButton(R.string.settings, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        mActivity.switchTo(SettingsFragment.newInstance());
                                    }
                                });
                            } else {
                                d.setMessage(e.getMessage());
                            }
                            d.show();
                        }
                    });
                    flashUtil.execute();
                } else {
                    /** Flashing needs to be handled specially (not standard flash method)*/
                    if (RashrApp.DEVICE.isFOTAFlashed()) {
                        /** Show warning if FOTAKernel will be flashed */
                        new AlertDialog.Builder(mContext)
                                .setTitle(R.string.warning)
                                .setMessage(R.string.fota)
                                .setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        final FlashUtil flashUtil = new FlashUtil(mActivity, recovery, FlashUtil.JOB_FLASH_RECOVERY);
                                        flashUtil.execute();
                                    }
                                })
                                .setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {

                                    }
                                })
                                .show();
                    } else if (RashrApp.DEVICE.isRecoveryOverRecovery()) {
                        mActivity.switchTo(ScriptManagerFragment.newInstance(mActivity, recovery));
                    }
                }
            }
        }
    }

    /**
     * Flashing kernel using FlashUtil
     *
     * @param kernel kernel image (appropriated for this device)
     */
    private void flashKernel(final File kernel) {
        if (kernel != null) {
            if (kernel.exists() && kernel.getName().endsWith(RashrApp.DEVICE.getKernelExt())
                    && !kernel.isDirectory()) {
                final FlashUtil flashUtil = new FlashUtil(mActivity, kernel, FlashUtil.JOB_FLASH_KERNEL);
                flashUtil.execute();
            }
        }
    }

    /**
     * optimizeLayout checks which cards need to be added to UI. So if you device doesn't support
     * kernel flashing optimizeLayout will not add it to UI.
     *
     * @param root RootView from Fragment
     * @throws NullPointerException layout can't be inflated
     */
    @SuppressLint("PrivateResource")
    public void optimizeLayout(View root) throws NullPointerException {

        CardUI RashrCards = (CardUI) root.findViewById(R.id.RashrCards);

        if (RashrApp.DEVICE.isRecoverySupported() || RashrApp.DEVICE.isKernelSupported()
                || BuildConfig.DEBUG) {
            /** If device is supported start setting up layout */
            setupSwipeUpdater(root);

            /** Avoid overlapping scroll on CardUI and SwipeRefreshLayout */
            RashrCards.getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {

                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    int topRowVerticalPosition = (view == null || view.getChildCount() == 0) ?
                            0 : view.getChildAt(0).getTop();
                    if (mSwipeUpdater != null)
                        mSwipeUpdater.setEnabled((topRowVerticalPosition >= 0));
                }
            });

            if (RashrApp.DEVICE.isRecoverySupported() || BuildConfig.DEBUG) {
                addRecoveryCards(RashrCards);
            }

            if (RashrApp.DEVICE.isKernelSupported() || BuildConfig.DEBUG) {
                addKernelCards(RashrCards);
            }
            //Device has been flashed over Rashr so you can choose previously used images
            if (getHistoryFiles().size() > 0) {
                final IconCard HistoryCard = new IconCard(getString(R.string.history),
                        R.drawable.ic_history, getString(R.string.history_description));
                HistoryCard.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showFlashHistory();
                    }
                });

                RashrCards.addCard(HistoryCard, true);
            }

        }
        addRebooterCards(RashrCards);
    }

    /**
     * Check if Device uses a Unified base like some Galaxy S4: htle, htltespr htltexx uses the same
     * sources so they can use the unified kernels and recoveries. Let the User choice which one is
     * the correct for him. PLEASE BE CAREFUL!
     */
    public void showUnifiedBuildsDialog() {

        final AppCompatDialog UnifiedBuildsDialog = new AppCompatDialog(mContext);
        UnifiedBuildsDialog.setTitle(R.string.make_choice);
        final ArrayList<String> DevName = new ArrayList<>();
        ArrayList<String> DevNamesCarriers = new ArrayList<>();

        UnifiedBuildsDialog.setContentView(R.layout.dialog_unified_build);
        ListView UnifiedList = (ListView) UnifiedBuildsDialog.findViewById(R.id.lvUnifiedList);
        ArrayAdapter<String> UnifiedAdapter = new ArrayAdapter<>(mContext,
                android.R.layout.simple_list_item_1, DevNamesCarriers);
        if (UnifiedList != null) {
            UnifiedList.setAdapter(UnifiedAdapter);

            UnifiedList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, final int position, long id) {
                    UnifiedBuildsDialog.dismiss();
                    final ProgressDialog reloading = new ProgressDialog(mContext);
                    reloading.setMessage(mContext.getString(R.string.reloading));
                    reloading.setCancelable(false);
                    reloading.show();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Common.setBooleanPref(mContext, Const.PREF_NAME,
                                    Const.PREF_KEY_SHOW_UNIFIED, false);
                            RashrApp.DEVICE.setName(DevName.get(position));
                            RashrApp.DEVICE.loadRecoveryList();
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    reloading.dismiss();
                                    mActivity.switchTo(FlashFragment.newInstance(mActivity));
                                }
                            });
                        }
                    }).start();

                }
            });
        }

        if (RashrApp.DEVICE.getManufacture().equals("samsung")) {
            String[] unifiedGalaxyS3 = {"d2lte", "d2att", "d2cri", "d2mtr",
                    "d2spr", "d2tmo", "d2usc", "d2vzw"};
            String[] unifiedGalaxyNote3 = {"hlte", "hltespr", "hltetmo", "hltevzw", "htlexx"};
            String[] unifiedGalaxyS4 = {"jflte", "jflteatt", "jfltecan", "jfltecri", "jfltecsp",
                    "jfltespr", "jfltetmo", "jflteusc", "jfltevzw", "jfltexx", "jgedlte"};
            String[] unifiedGalaxyNote4 = {"trlte", "trltecan", "trltedt", "trltexx", "trltespr",
                    "trltetmo", "trltevzw", "trlteusc"};
            if (Common.stringEndsWithArray(RashrApp.DEVICE.getName(), unifiedGalaxyS3)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyS3));
            } else if (Common.stringEndsWithArray(RashrApp.DEVICE.getName(), unifiedGalaxyS3)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyNote3));
            } else if (Common.stringEndsWithArray(RashrApp.DEVICE.getName(), unifiedGalaxyS4)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyS4));
            } else if (Common.stringEndsWithArray(RashrApp.DEVICE.getName(), unifiedGalaxyNote4)) {
                DevName.addAll(Arrays.asList(unifiedGalaxyNote4));
            }
        }

        if (RashrApp.DEVICE.getManufacture().equals("motorola")) {
            String[] unifiedMsm8960 = {"moto_msm8960"};
            if (RashrApp.DEVICE.getBOARD().equals("msm8960")) {
                DevName.addAll(Arrays.asList(unifiedMsm8960));
            }
        }

        for (String i : DevName) {
            if (i.contains("att")) {
                DevNamesCarriers.add(i + " (AT&T Mobility)");
            } else if (i.contains("can")) {
                DevNamesCarriers.add(i + " (Canada)");
            } else if (i.contains("cri")) {
                DevNamesCarriers.add(i + " (Cricket Wireless)");
            } else if (i.contains("csp")) {
                DevNamesCarriers.add(i + " (C Spire Wireless)");
            } else if (i.contains("mtr")) {
                DevNamesCarriers.add(i + " (MetroPCS)");
            } else if (i.contains("spr")) {
                DevNamesCarriers.add(i + " (Sprint Corporation)");
            } else if (i.contains("tmo")) {
                DevNamesCarriers.add(i + " (T-Mobile US)");
            } else if (i.contains("usc")) {
                DevNamesCarriers.add(i + " (U.S. Cellular)");
            } else if (i.contains("vzw")) {
                DevNamesCarriers.add(i + " (Verizon Wireless)");
            } else if (i.contains("xx")) {
                DevNamesCarriers.add(i + " (International)");
            } else if (i.contains("ged")) {
                DevNamesCarriers.add(i + " (Google Play Edition)");
            } else if (i.contains("dt")) {
                DevNamesCarriers.add(i + " (India)");
            } else {
                DevNamesCarriers.add(i + " (Unified)");
            }
        }
        AppCompatButton KeepCurrent = (AppCompatButton) UnifiedBuildsDialog.findViewById(R.id.bKeepCurrent);
        if (KeepCurrent != null) {
            KeepCurrent.setText(String.format(getString(R.string.keep_current_name), RashrApp.DEVICE.getName()));
            KeepCurrent.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Common.setBooleanPref(mContext, Const.PREF_NAME, Const.PREF_KEY_SHOW_UNIFIED, false);
                    UnifiedBuildsDialog.dismiss();
                }
            });
        }

        if (DevName.size() > 0) {
            UnifiedBuildsDialog.show();
            UnifiedBuildsDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Common.setBooleanPref(mContext, Const.PREF_NAME, Const.PREF_KEY_SHOW_UNIFIED,
                            false);
                }
            });
        }
    }

    public void setupSwipeUpdater(View root) {
        mSwipeUpdater = (SwipeRefreshLayout) root.findViewById(R.id.swipe_updater);
        mSwipeUpdater.setColorSchemeResources(
                R.color.custom_blue,
                R.color.golden,
                R.color.custom_blue,
                android.R.color.darker_gray);
        mSwipeUpdater.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                catchUpdates();
            }
        });

    }

    /**
     * addRecoveryCards checks which recovery systems are supported by your device for example:
     * Galaxy S6 (SM-G920F) supports TWRP but isn't supported by CWM so addRecoveryCards will add
     * TWRP Card and Recovery From Storage card.
     *
     * @param cardUI Where should be the cards added
     */
    public void addRecoveryCards(CardUI cardUI) {
        if (RashrApp.DEVICE.isXZDualRecoverySupported() || BuildConfig.DEBUG) {
            final IconCard XZCard = new IconCard(getString(R.string.xzdualrecovery), R.drawable.ic_xzdual,
                    getString(R.string.xzdual_describtion));
            XZCard.setData(Device.REC_SYS_XZDUAL);
            XZCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecoverySystemFragment.PARAM_TITLE, XZCard.getData().toString());
                    bundle.putString(RecoverySystemFragment.PARAM_DEV, getString(R.string.dev_name_xzdual));
                    bundle.putString(RecoverySystemFragment.PARAM_DESC, getString(R.string.xzdual_describtion));
                    bundle.putStringArrayList(RecoverySystemFragment.PARAM_VERSIONS, RashrApp.DEVICE.getXZDualRecoveryVersions());
                    bundle.putInt(RecoverySystemFragment.PARAM_LOGO, R.drawable.ic_xzdual);
                    bundle.putString(RecoverySystemFragment.PARAM_IMG_PATH, Const.PathToXZDual.toString());
                    mActivity.switchTo(RecoverySystemFragment.newInstance(bundle), true);
                }
            });
            cardUI.addCard(XZCard, true);
        }
        if (RashrApp.DEVICE.isCwmRecoverySupported() || BuildConfig.DEBUG) {
            final IconCard CWMCard = new IconCard(getString(R.string.sCWM), R.drawable.ic_cwm,
                    getString(R.string.cwm_description));
            CWMCard.setData(Device.REC_SYS_CWM);
            CWMCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecoverySystemFragment.PARAM_TITLE, CWMCard.getData().toString());
                    bundle.putString(RecoverySystemFragment.PARAM_DEV, getString(R.string.dev_name_cwm));
                    bundle.putString(RecoverySystemFragment.PARAM_DESC, getString(R.string.cwm_description));
                    bundle.putStringArrayList(RecoverySystemFragment.PARAM_VERSIONS, RashrApp.DEVICE.getCwmRecoveryVersions());
                    bundle.putString(RecoverySystemFragment.PARAM_SCREENSHOT_URL, Const.CWM_SCREENSHOT_URL);
                    bundle.putInt(RecoverySystemFragment.PARAM_LOGO, R.drawable.ic_cwm);
                    bundle.putString(RecoverySystemFragment.PARAM_IMG_PATH, Const.PathToCWM.toString());
                    mActivity.switchTo(RecoverySystemFragment.newInstance(bundle), true);
                }
            });
            cardUI.addCard(CWMCard, true);
        }
        if (RashrApp.DEVICE.isTwrpRecoverySupported() || BuildConfig.DEBUG) {
            final IconCard TWRPCard = new IconCard(getString(R.string.sTWRP), R.drawable.ic_twrp,
                    getString(R.string.twrp_description));
            TWRPCard.setData(Device.REC_SYS_TWRP);
            TWRPCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecoverySystemFragment.PARAM_TITLE, TWRPCard.getData().toString());
                    bundle.putString(RecoverySystemFragment.PARAM_DEV, getString(R.string.dev_name_twrp));
                    bundle.putString(RecoverySystemFragment.PARAM_DESC, getString(R.string.twrp_description));
                    bundle.putStringArrayList(RecoverySystemFragment.PARAM_VERSIONS, RashrApp.DEVICE.getTwrpRecoveryVersions());
                    bundle.putString(RecoverySystemFragment.PARAM_SCREENSHOT_URL, Const.TWRP_SCREENSHOT_URL);
                    bundle.putInt(RecoverySystemFragment.PARAM_LOGO, R.drawable.ic_twrp);
                    bundle.putString(RecoverySystemFragment.PARAM_IMG_PATH, Const.PathToTWRP.toString());
                    mActivity.switchTo(RecoverySystemFragment.newInstance(bundle), true);
                }
            });
            cardUI.addCard(TWRPCard, true);
        }
        if (RashrApp.DEVICE.isPhilzRecoverySupported() || BuildConfig.DEBUG) {
            final SimpleCard PHILZCard = new SimpleCard(getString(R.string.sPhilz),
                    getString(R.string.philz_description));
            PHILZCard.setData(Device.REC_SYS_PHILZ);
            PHILZCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecoverySystemFragment.PARAM_TITLE, PHILZCard.getData().toString());
                    bundle.putString(RecoverySystemFragment.PARAM_DEV, getString(R.string.dev_name_philz));
                    bundle.putString(RecoverySystemFragment.PARAM_DESC, getString(R.string.philz_description));
                    bundle.putStringArrayList(RecoverySystemFragment.PARAM_VERSIONS, RashrApp.DEVICE.getPhilzRecoveryVersions());
                    bundle.putString(RecoverySystemFragment.PARAM_IMG_PATH, Const.PathToPhilz.toString());
                    mActivity.switchTo(RecoverySystemFragment.newInstance(bundle), true);
                }
            });
            cardUI.addCard(PHILZCard, true);
        }
        if (RashrApp.DEVICE.isCmRecoverySupported() || BuildConfig.DEBUG) {
            final IconCard CMCard = new IconCard(getString(R.string.cm_recovery), R.drawable.ic_cm,
                    getString(R.string.cm_recovery_description));
            CMCard.setData(Device.REC_SYS_CM);
            CMCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecoverySystemFragment.PARAM_TITLE, CMCard.getData().toString());
                    bundle.putString(RecoverySystemFragment.PARAM_DEV, getString(R.string.dev_name_cm));
                    bundle.putString(RecoverySystemFragment.PARAM_DESC, getString(R.string.cm_recovery_description));
                    bundle.putStringArrayList(RecoverySystemFragment.PARAM_VERSIONS, RashrApp.DEVICE.getCmRecoveriyVersions());
                    bundle.putInt(RecoverySystemFragment.PARAM_LOGO, R.drawable.ic_cm);
                    bundle.putString(RecoverySystemFragment.PARAM_IMG_PATH, Const.PathToCM.toString());
                    mActivity.switchTo(RecoverySystemFragment.newInstance(bundle), true);
                }
            });
            cardUI.addCard(CMCard, true);
        }
        if (RashrApp.DEVICE.isStockRecoverySupported() || BuildConfig.DEBUG) {
            final IconCard StockCard = new IconCard(getString(R.string.stock_recovery),
                    R.drawable.ic_update, getString(R.string.stock_recovery_description));
            StockCard.setData(Device.REC_SYS_STOCK);
            StockCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Bundle bundle = new Bundle();
                    bundle.putString(RecoverySystemFragment.PARAM_TITLE, StockCard.getData().toString());
                    bundle.putString(RecoverySystemFragment.PARAM_DEV, RashrApp.DEVICE.getManufacture());
                    bundle.putString(RecoverySystemFragment.PARAM_DESC, getString(R.string.stock_recovery_description));
                    bundle.putStringArrayList(RecoverySystemFragment.PARAM_VERSIONS, RashrApp.DEVICE.getStockRecoveryVersions());
                    bundle.putInt(RecoverySystemFragment.PARAM_LOGO, R.drawable.ic_update);
                    bundle.putString(RecoverySystemFragment.PARAM_IMG_PATH, Const.PathToStockRecovery.toString());
                    mActivity.switchTo(RecoverySystemFragment.newInstance(bundle));
                }
            });
            cardUI.addCard(StockCard, true);
        }

        final IconCard OtherCard = new IconCard(getString(R.string.other_recovery),
                R.drawable.ic_folder,
                getString(R.string.other_storage_description));
        OtherCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bFlashOtherRecovery(v);
            }
        });
        cardUI.addCard(OtherCard, true);
    }

    /**
     * addKernelCards checks wich kernels are supported by your device
     *
     * @param cardUI Where should be the cards added
     */
    public void addKernelCards(CardUI cardUI) {
        if (RashrApp.DEVICE.isStockKernelSupported() || BuildConfig.DEBUG) {
            final IconCard StockCard = new IconCard(getString(R.string.stock_kernel), R.drawable.ic_stock_kernel,
                    getString(R.string.stock_kernel_description));
            StockCard.setData("stock");
            StockCard.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FlashSupportedKernel(StockCard);
                }
            });
            cardUI.addCard(StockCard, true);
        }

        final IconCard OtherCard = new IconCard(getString(R.string.other_kernel),
                R.drawable.ic_folder,
                getString(R.string.other_storage_description));
        OtherCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bFlashOtherKernel(v);
            }
        });

        cardUI.addCard(OtherCard, true);
    }

    /**
     * Add cards for reboot device: Reboot to Bootloader, Reboot to Recovery, Reboot, Shutdown
     *
     * @param cardUI Where should be the cards added
     */
    public void addRebooterCards(CardUI cardUI) {
        SimpleCard Reboot = new SimpleCard(getString(R.string.sReboot),
                getString(R.string.reboot_description));
        Reboot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.reboot_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            RashrApp.TOOLBOX.reboot(Toolbox.REBOOT_REBOOT);
                        } catch (Exception e) {
                            Toast.makeText(mContext, R.string.reboot_failed, Toast.LENGTH_SHORT).show();
                            RashrApp.ERRORS.add(e.toString());
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        SimpleCard RebootRecovery = new SimpleCard(getString(R.string.sRebootRecovery),
                getString(R.string.reboot_recovery_description));
        RebootRecovery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.reboot_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            RashrApp.TOOLBOX.reboot(Toolbox.REBOOT_RECOVERY);
                        } catch (Exception e) {
                            Toast.makeText(mContext, R.string.reboot_failed, Toast.LENGTH_SHORT).show();
                            RashrApp.ERRORS.add(e.toString());
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        SimpleCard RebootBootloader = new SimpleCard(getString(R.string.sRebootBootloader),
                getString(R.string.reboot_bootloader_description));
        RebootBootloader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.reboot_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            RashrApp.TOOLBOX.reboot(Toolbox.REBOOT_BOOTLOADER);
                        } catch (Exception e) {
                            Toast.makeText(mContext, R.string.reboot_failed, Toast.LENGTH_SHORT).show();
                            RashrApp.ERRORS.add(e.toString());
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        SimpleCard Shutdown = new SimpleCard(getString(R.string.sRebootShutdown),
                getString(R.string.shutdown_description));
        Shutdown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final AlertDialog.Builder ConfirmationDialog = new AlertDialog.Builder(mContext);
                ConfirmationDialog.setTitle(R.string.warning);
                ConfirmationDialog.setMessage(R.string.shutdown_confirmation);
                ConfirmationDialog.setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            RashrApp.TOOLBOX.reboot(Toolbox.REBOOT_SHUTDOWN);
                        } catch (Exception e) {
                            Toast.makeText(mContext, R.string.reboot_failed, Toast.LENGTH_SHORT).show();
                            RashrApp.ERRORS.add(e.toString());
                        }
                    }
                });
                ConfirmationDialog.setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                ConfirmationDialog.show();
            }
        });
        cardUI.addCard(Reboot, true);
        cardUI.addCard(RebootRecovery, true);
        cardUI.addCard(RebootBootloader, true);
        cardUI.addCard(Shutdown, true);
    }

    public void setActivity(RashrActivity activity) {
        mActivity = activity;
        mContext = activity;
    }

    /**
     * Checking if there are new Kernel and Recovery images to download.
     * Download new list of recoveries and kernels if user want and reload interface.
     * The lists are placed in  dslnexus.de/Android/recovery_links
     * dslnexus.de/Android/kernel_sums
     */
    public void catchUpdates() {
        if (mSwipeUpdater != null) mSwipeUpdater.setRefreshing(true);
        final Thread updateThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /** Check changes on server */
                    final URL recoveryUrl = new URL(Const.RECOVERY_SUMS_URL);
                    URLConnection recoveryCon = recoveryUrl.openConnection();
                    long recoveryListSize = recoveryCon.getContentLength();         //returns size of file on server
                    long recoveryListLocalSize = Const.RecoveryCollectionFile.length();   //returns size of local file
                    if (recoveryListSize > 0) {
                        isRecoveryListUpToDate = recoveryListLocalSize == recoveryListSize;
                    }
                    final URL kernelUrl = new URL(Const.KERNEL_SUMS_URL);
                    URLConnection kernelCon = kernelUrl.openConnection();
                    long kernelListSize = kernelCon.getContentLength();
                    long kernelListLocalSize = Const.KernelCollectionFile.length();
                    if (kernelListSize > 0) {
                        isKernelListUpToDate = kernelListLocalSize == kernelListSize;
                    }
                } catch (IOException e) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast
                                    .makeText(mContext, R.string.check_connection, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    });
                    if (e.toString() != null) {
                        RashrApp.ERRORS.add(Const.RASHR_TAG + " Error while checking updates: " + e);
                    } else {
                        RashrApp.ERRORS.add(Const.RASHR_TAG + " Error while checking updates");
                    }
                }
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        /** Something on server changed */
                        if (!isRecoveryListUpToDate || !isKernelListUpToDate) {
                            /** Counting current images */
                            final int img_count = RashrApp.DEVICE.getStockRecoveryVersions().size()
                                    + RashrApp.DEVICE.getCwmRecoveryVersions().size()
                                    + RashrApp.DEVICE.getTwrpRecoveryVersions().size()
                                    + RashrApp.DEVICE.getPhilzRecoveryVersions().size()
                                    + RashrApp.DEVICE.getStockKernelVersions().size()
                                    + RashrApp.DEVICE.getCmRecoveriyVersions().size();
                            final URL recoveryURL;
                            final URL kernelURL;
                            try {
                                recoveryURL = new URL(Const.RECOVERY_SUMS_URL);
                                kernelURL = new URL(Const.KERNEL_SUMS_URL);
                            } catch (MalformedURLException e) {
                                RashrApp.ERRORS.add(e.toString());
                                return;
                            }
                            /** Download the new lists */
                            final Downloader rDownloader = new Downloader(recoveryURL, Const.RecoveryCollectionFile);
                            rDownloader.setOverrideFile(true);
                            rDownloader.setOnDownloadListener(new Downloader.OnDownloadListener() {
                                @Override
                                public void onSuccess(File file) {
                                    RashrApp.DEVICE.loadRecoveryList();
                                    isRecoveryListUpToDate = true;
                                    final Downloader kDownloader = new Downloader(kernelURL, Const.KernelCollectionFile);
                                    kDownloader.setOverrideFile(true);
                                    kDownloader.setOnDownloadListener(new Downloader.OnDownloadListener() {
                                        @Override
                                        public void onSuccess(File file) {
                                            RashrApp.DEVICE.loadKernelList();
                                            isKernelListUpToDate = true;
                                            /** Counting added images (after update) */
                                            final int new_img_count = (RashrApp.DEVICE.getStockRecoveryVersions().size()
                                                    + RashrApp.DEVICE.getCwmRecoveryVersions().size()
                                                    + RashrApp.DEVICE.getTwrpRecoveryVersions().size()
                                                    + RashrApp.DEVICE.getPhilzRecoveryVersions().size()
                                                    + RashrApp.DEVICE.getStockKernelVersions().size())
                                                    + RashrApp.DEVICE.getCmRecoveriyVersions().size() - img_count;
                                            mActivity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (isAdded()) {
                                                        Toast
                                                                .makeText(mActivity, String.format(getString(R.string.new_imgs_loaded),
                                                                        String.valueOf(new_img_count)), Toast.LENGTH_SHORT)
                                                                .show();
                                                    }
                                                    if (mSwipeUpdater != null) {
                                                        mSwipeUpdater.setRefreshing(false);
                                                    }
                                                }
                                            });
                                        }

                                        @Override
                                        public void onFail(final Exception e) {
                                            String msg;
                                            if (e != null) {
                                                msg = e.getMessage();
                                            } else {
                                                msg = "Error occurred while loading new Kernel Lists";
                                            }
                                            Toast
                                                    .makeText(mActivity, msg, Toast.LENGTH_SHORT)
                                                    .show();
                                            if (mSwipeUpdater != null) {
                                                mSwipeUpdater.setRefreshing(false);
                                            }
                                        }
                                    });
                                    kDownloader.download();
                                }

                                @Override
                                public void onFail(final Exception e) {
                                    String msg;
                                    if (e != null) {
                                        msg = e.getMessage();
                                    } else {
                                        msg = "Error occurred while loading new Recovery Lists";
                                    }
                                    Toast
                                            .makeText(mActivity, msg, Toast.LENGTH_SHORT)
                                            .show();
                                    if (mSwipeUpdater != null) {
                                        mSwipeUpdater.setRefreshing(false);
                                    }
                                }
                            });
                            rDownloader.download();
                        } else {
                            /** Lists are up to date */
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!Common.getBooleanPref(mContext, Const.PREF_NAME,
                                            Const.PREF_KEY_HIDE_UPDATE_HINTS)) {
                                        Toast
                                                .makeText(mContext, R.string.uptodate, Toast.LENGTH_SHORT)
                                                .show();
                                    }
                                    if (mSwipeUpdater != null) {
                                        mSwipeUpdater.setRefreshing(false);
                                    }
                                }
                            });
                        }
                    }
                });
            }
        });
        updateThread.start();
    }

    /**
     * @return A ArrayList<File> with the last existing images that the user has Flashed
     */
    private ArrayList<File> getHistoryFiles() {
        ArrayList<File> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            File tmp = new File(Common.getStringPref(mContext, Const.PREF_NAME,
                    Const.PREF_KEY_HISTORY + String.valueOf(i)));
            if (tmp.exists() && !tmp.isDirectory()) {
                /* Add file to list */
                history.add(tmp);
            } else {
                /* File has been deleted, clear information */
                Common.setStringPref(mContext, Const.PREF_NAME,
                        Const.PREF_KEY_HISTORY + String.valueOf(i), "");
            }
        }

        return history;
    }
}


package com.example.ui.i18n

import androidx.compose.runtime.Immutable

@Immutable
interface AppStrings {
    val appTitle: String
    val navDashboard: String
    val navServers: String
    val navSplitTunnel: String
    val navSettings: String
    val statusDisconnected: String
    val statusConnecting: String
    val statusConnected: String
    val statusReconnecting: String
    val statusFirewallLocked: String
    val statusDisconnecting: String
    val statusError: String
    val tapToConnect: String
    val tapToDisconnect: String
    val tapToUnlockFirewall: String
    val selectServerPrompt: String
    val activeProfileLabel: String
    val noProfileSelected: String
    val protectionCardTitle: String
    val killSwitchTitle: String
    val killSwitchDesc: String
    val autoReconnectTitle: String
    val autoReconnectDesc: String
    val firewallLockedBanner: String
    val systemAlwaysOnVpn: String
    val exitLocationTitle: String
    val exitIpLoading: String
    val exitIpPlaceholder: String
    val refreshIp: String
    val ispLabel: String
    val liveSpeedTitle: String
    val downloadSpeed: String
    val uploadSpeed: String
    val totalDownloaded: String
    val totalUploaded: String
    val sessionDuration: String
    val activeStreams: String
    val tunnelSummaryFull: String
    val tunnelSummaryInclude: (Int) -> String
    val tunnelSummaryExclude: (Int) -> String
    val batteryLowPowerBadge: String
    val batteryBalancedBadge: String
    val serversTitle: String
    val filterAll: String
    val filterMulti: String
    val testAllConnections: String
    val stopTest: String
    val testingProgress: (Int, Int) -> String
    val testingNow: String
    val testFailed: String
    val testDialogTitle: String
    val testDialogMessage: String
    val autoConnectFastestCheck: String
    val startTestButton: String
    val autoConnectedFastestMsg: (String) -> String
    val createLoadBalance: String
    val loadBalanceStrategyLabel: String
    val strategyFailover: String
    val strategyRoundRobin: String
    val strategyLeastLatency: String
    val strategyRandom: String
    val selectMembersHint: String
    val membersCountBadge: (Int) -> String
    val needTwoMembersMsg: String
    val addServer: String
    val importFromLink: String
    val pasteConfigHint: String
    val importFromClipboard: String
    val importButton: String
    val noServersTitle: String
    val noServersSubtitle: String
    val testLatency: String
    val latencyMs: (Int) -> String
    val shareQr: String
    val copyLink: String
    val copiedToast: String
    val editServer: String
    val deleteServer: String
    val protocolLabel: String
    val serverNameLabel: String
    val hostLabel: String
    val portLabel: String
    val uuidOrUserLabel: String
    val passwordOrSecretLabel: String
    val privateKeyLabel: String
    val transportLabel: String
    val tlsEnabledLabel: String
    val sniLabel: String
    val wsHostLabel: String
    val wsPathLabel: String
    val cipherLabel: String
    val allowInsecureLabel: String
    val saveButton: String
    val cancelButton: String
    val splitTunnelTitle: String
    val splitModeFull: String
    val splitModeFullDesc: String
    val splitModeInclude: String
    val splitModeIncludeDesc: String
    val splitModeExclude: String
    val splitModeExcludeDesc: String
    val searchAppsHint: String
    val showSystemApps: String
    val selectAll: String
    val clearAll: String
    val appsSelectedCount: (Int) -> String
    val reconnectToApply: String
    val settingsTitle: String
    val batterySectionTitle: String
    val batteryUltraLowTitle: String
    val batteryUltraLowDesc: String
    val batteryBalancedTitle: String
    val batteryBalancedDesc: String
    val systemBatterySettings: String
    val networkSectionTitle: String
    val dnsPrimaryLabel: String
    val dnsSecondaryLabel: String
    val mtuLabel: String
    val autoCheckIpLabel: String
    val languageSectionTitle: String
    val logsTitle: String
    val clearLogs: String
    val noLogsYet: String
    val importedCountMsg: (Int) -> String
    val importFailedMsg: String
    val exportConfigs: String
    val importConfigs: String
    val exportDialogTitle: String
    val exportDialogDesc: (Int) -> String
    val encryptionPasswordLabel: String
    val encryptionPasswordHint: String
    val passwordTooShortError: String
    val saveToFileButton: String
    val copyJsonButton: String
    val exportSavedToast: String
    val exportCopiedToast: String
    val noServersToExportMsg: String
    val importDialogSubtitle: String
    val chooseFileButton: String
    val decryptionPasswordLabel: String
    val decryptionPasswordRequired: String
    val wrongDecryptionPasswordMsg: String
    val invalidBackupFileMsg: String
    val showPasswordDesc: String
    val hidePasswordDesc: String
    val themeSectionTitle: String
    val themeSystem: String
    val themeDark: String
    val themeLight: String
    val switchThemeDesc: String
    val splashSubtitle: String
    val trafficTabLive: String
    val trafficTabRules: String
    val trafficFilterAll: String
    val trafficFilterPassed: String
    val trafficFilterFailed: String
    val trafficStatusPassed: String
    val trafficStatusFailed: String
    val trafficEmptyDisconnected: String
    val trafficEmptyWaiting: String
    val trafficSystemAppLabel: String
    val trafficSummaryCounts: (Int, Int) -> String
    val crashlyticsSectionTitle: String
    val crashlyticsEnabledLabel: String
    val crashlyticsEnabledDesc: String
    val pasteClipboardQuickButton: String
    val clipboardEmptyMsg: String
    val clipboardNoValidConfigMsg: String
    val importedWithTypesMsg: (Int, String) -> String
    val subscriptionsButton: String
    val subscriptionsCountButton: (Int) -> String
    val subscriptionsTitle: String
    val addSubscriptionTitle: String
    val editSubscriptionTitle: String
    val addAndSyncButton: String
    val saveAndSyncButton: String
    val updateAllSubscriptions: String
    val updateSubscription: String
    val deleteSubscription: String
    val subscriptionNameLabel: String
    val subscriptionNameHint: String
    val subscriptionUrlLabel: String
    val subscriptionUrlHint: String
    val subscriptionServersCount: (Int) -> String
    val subscriptionUpdatedSuccess: (String, Int) -> String
    val subscriptionUpdateAllResult: (Int, Int) -> String
    val noSubscriptionsTitle: String
    val noSubscriptionsSubtitle: String
    val subscriptionUrlDetectedMsg: String
    val updateRequiredTitle: String
    val updateAvailableTitle: String
    val updateForceDesc: String
    val updateOptionalDesc: String
    val updateDownloadButton: String
    val updateLaterButton: String
    val updateInvalidUrlMsg: String
    val firebaseServicesSectionTitle: String
    val checkUpdatesButton: String
    val noUpdateAvailableToast: String
    val testAnalyticsButton: String
    val analyticsTestSentToast: String
    val analyticsTestFailedToast: String
    val copyFirebaseIdsButton: String
    val firebaseIdsCopiedToast: String
    val firebaseIdsUnavailableToast: String
    val remoteConfigFetchFailedToast: String
    val shortcutsSectionTitle: String
    val pinWidgetButton: String
    val pinWidgetNotSupportedToast: String
    val addQuickTileButton: String
    val quickTileAddedToast: String
    val quickTileManualHintToast: String
    val firebaseStatusLabel: String
    val pingAllButton: String
    val stopPingButton: String
    val pingingProgress: (Int, Int) -> String
    val sortByFastestChip: String
    val fastestBadge: String
    val pingTimeoutLabel: String
    val tapToPingLabel: String
    val speedTestActionDesc: String
    val lowBatteryModeTitle: String
    val lowBatteryModeDesc: String
    val lowBatteryActiveBadge: (Int) -> String
    val lowBatteryStandbyBadge: (Int) -> String
    val lowBatteryBannerTitle: (Int) -> String
    val lowBatteryBannerSubtitle: (Int) -> String
    val lowBatteryServersBanner: (Int) -> String
    val lowBatteryBenchmarkPausedToast: String
    val dualVpnTitle: String
    val dualVpnActiveLabel: String
    val dualVpnStandbyLabel: String
    val dualVpnReadyLabel: String
    val dualVpnWarmingLabel: String
    val switchVpnNowButton: String
    val multiTunnelSwitchesCount: (Int) -> String
    val systemAlwaysOnVpnDesc: String
    val serversTabMyServers: String
    val serversTabFreeProviders: String
    val freeProvidersBannerTitle: String
    val freeProvidersBannerSubtitle: String
    val freeProviderPingConnectButton: String
    val freeProviderFetching: String
    val freeProviderPingingProgress: (Int, Int) -> String
    val freeProviderVerifyingBest: String
    val freeProviderReachableCount: (Int) -> String
    val freeProviderConnectedToast: (String, Int) -> String
    val freeProviderNoWorkingServerMsg: (String) -> String
    val freeProviderIranBadge: String
    val freeProviderFilterAll: String
    val freeProviderFilterIran: String
    val freeProviderFilterProxy: String
    val freeProviderUpdatedDateLabel: (String) -> String
    val freeProviderDefaultDateLabel: (String) -> String
    val freeProviderCachedCount: (Int) -> String
    val freeProviderBackgroundSyncBanner: (String, Int, Int) -> String
    val freeProviderUsingOfflineFallback: String
    val freeProviderUpdatingNow: String
    val freeProvidersDisclaimerTitle: String
    val freeProvidersDisclaimerBody: String
    val serverSpeedDetailsTitle: String
    val quickWidgetShortButton: String
    val controlCenterShortButton: String
    // Feature 1: Smart Routing (Iran & LAN Bypass)
    val smartRoutingSectionTitle: String
    val bypassIranTitle: String
    val bypassIranDesc: String
    val bypassLanTitle: String
    val bypassLanDesc: String
    val customBypassDomainsLabel: String
    val customBypassDomainsHint: String
    val bypassedLiveBadge: (Int) -> String
    // Feature 2: Smart Auto-Connect & Auto-Failover
    val smartAutoConnectButton: String
    val smartAutoConnectingLabel: String
    val smartAutoConnectNoServerToast: String
    val smartAutoConnectedToast: (String, Int) -> String
    val smartFailoverTitle: String
    val smartFailoverDesc: String
    // Feature 3: Custom DNS / DoH Presets & AdBlock DNS
    val dnsPresetLabel: String
    val adBlockDnsTitle: String
    val adBlockDnsDesc: String
    val adsBlockedLiveBadge: (Int) -> String
    val customDohUrlLabel: String
    val customDohUrlHint: String
    // Feature 4: Search, Sort, Favorites & Server Cleaner
    val searchServersHint: String
    val filterFavoritesChip: String
    val sortDefaultChip: String
    val sortPingChip: String
    val sortCountryChip: String
    val favoriteToggleDesc: String
    val cleanServersButton: String
    val cleanServersDialogTitle: String
    val cleanServersDialogBody: (Int, Int) -> String
    val cleanServersConfirmButton: String
    val cleanServersNothingToast: String
    val cleanServersDoneToast: (Int) -> String
    // Feature 5: Live Camera QR Scanner
    val scanQrButton: String
    val qrScannerTitle: String
    val qrScannerSubtitle: String
    val qrCameraPermissionNeeded: String
    val qrGrantPermissionButton: String
    val qrTorchToggleDesc: String
    // Feature 6: Data Usage History & Enhanced Live Logs
    val dataUsageSectionTitle: String
    val dataUsageToday: String
    val dataUsageWeek: String
    val dataUsageMonth: String
    val dataUsageLast7Days: String
    val dataUsageResetButton: String
    val logFilterAll: String
    val logFilterErrors: String
    val logFilterRouting: String
    val logFilterFailover: String
    val logFilterAdBlock: String
    val copyLogsButton: String
    val logsCopiedToast: String
}

object FarsiStrings : AppStrings {
    override val appTitle = "Baldr VPN"
    override val navDashboard = "اتصال"
    override val navServers = "سرورها"
    override val navSplitTunnel = "ترافیک"
    override val navSettings = "تنظیمات"
    override val statusDisconnected = "متصل نیست"
    override val statusConnecting = "در حال اتصال..."
    override val statusConnected = "متصل و ایمن"
    override val statusReconnecting = "در حال اتصال مجدد..."
    override val statusFirewallLocked = "فایروال فعال (مسدود)"
    override val statusDisconnecting = "در حال قطع..."
    override val statusError = "خطای اتصال"
    override val tapToConnect = "لمس برای اتصال"
    override val tapToDisconnect = "لمس برای قطع اتصال"
    override val tapToUnlockFirewall = "لمس برای قطع کامل و آزادسازی فایروال"
    override val selectServerPrompt = "ابتدا یک سرور اضافه یا انتخاب کنید"
    override val activeProfileLabel = "سرور فعال"
    override val noProfileSelected = "سروری انتخاب نشده است"
    override val protectionCardTitle = "محافظت و پایداری اتصال"
    override val killSwitchTitle = "فایروال (Kill Switch)"
    override val killSwitchDesc = "اگر VPN قطع شد ارتباط اینترنت مسدود بماند و نشت نکند"
    override val autoReconnectTitle = "اتصال مجدد خودکار"
    override val autoReconnectDesc = "در صورت قطع شدن یا تغییر شبکه، خودکار دوباره وصل شود"
    override val firewallLockedBanner = "فایروال فعال است: ترافیک غیر-VPN تا برقراری اتصال مسدود شده است."
    override val systemAlwaysOnVpn = "تنظیم Always-On سیستم"
    override val exitLocationTitle = "موقعیت و آی‌پی خروجی VPN"
    override val exitIpLoading = "در حال بررسی آی‌پی خروجی..."
    override val exitIpPlaceholder = "پس از اتصال، آی‌پی و پرچم کشور نمایش داده می‌شود"
    override val refreshIp = "بروزرسانی IP"
    override val ispLabel = "سرویس‌دهنده"
    override val liveSpeedTitle = "ترافیک و سرعت لحظه‌ای"
    override val downloadSpeed = "سرعت دانلود"
    override val uploadSpeed = "سرعت آپلود"
    override val totalDownloaded = "کل دانلود"
    override val totalUploaded = "کل آپلود"
    override val sessionDuration = "مدت اتصال"
    override val activeStreams = "کانال فعال"
    override val tunnelSummaryFull = "تانل کل سیستم"
    override val tunnelSummaryInclude: (Int) -> String = { count -> "فقط $count اپ انتخاب‌شده" }
    override val tunnelSummaryExclude: (Int) -> String = { count -> "همه به جز $count اپ" }
    override val batteryLowPowerBadge = "مصرف باتری فوق‌کم"
    override val batteryBalancedBadge = "حالت متعادل"
    override val serversTitle = "پروفایل‌های سرور"
    override val filterAll = "همه"
    override val filterMulti = "ترکیبی (لودبالانس)"
    override val testAllConnections = "تست همه سرورها"
    override val stopTest = "توقف تست"
    override val testingProgress: (Int, Int) -> String = { done, total -> "در حال تست اتصال و دانلود: $done از $total" }
    override val testingNow = "در حال تست..."
    override val testFailed = "ناموفق"
    override val testDialogTitle = "تست سرعت و اتصال سرورها"
    override val testDialogMessage = "در این تست تک‌تک سرورها پینگ گرفته شده و یک فایل تست برای سنجش سرعت واقعی دانلود دریافت می‌شود."
    override val autoConnectFastestCheck = "اتصال خودکار به سروری با بیشترین سرعت پس از پایان تست"
    override val startTestButton = "شروع تست"
    override val autoConnectedFastestMsg: (String) -> String = { name -> "اتصال خودکار به سریع‌ترین سرور: $name" }
    override val createLoadBalance = "پروفایل ترکیبی (لودبالانس)"
    override val loadBalanceStrategyLabel = "روش اتصال چندسروره (Multi-Mode)"
    override val strategyFailover = "اتصال دوگانه همزمان (فعال + تعویض خودکار)"
    override val strategyRoundRobin = "چرخشی همزمان (Round-Robin)"
    override val strategyLeastLatency = "کمترین پینگ + توزیع بار"
    override val strategyRandom = "تصادفی موازی (Parallel)"
    override val selectMembersHint = "حداقل ۲ سرور (SOCKS / HTTP / V2Ray / SSH) برای ترکیب انتخاب کنید:"
    override val membersCountBadge: (Int) -> String = { count -> "$count سرور همزمان" }
    override val needTwoMembersMsg = "برای ساخت پروفایل ترکیبی حداقل ۲ سرور انتخاب کنید"
    override val addServer = "افزودن دستی"
    override val importFromLink = "افزودن از لینک"
    override val pasteConfigHint = "لینک vmess://, vless://, trojan://, ss://, socks5://, socks4://, http:// یا ssh:// را وارد کنید"
    override val importFromClipboard = "جایگذاری از کلیپ‌بورد"
    override val importButton = "وارد کردن"
    override val noServersTitle = "هنوز سروری ثبت نشده است"
    override val noServersSubtitle = "از دکمه + برای وارد کردن لینک V2Ray، SSH، SOCKS یا HTTP Proxy استفاده کنید"
    override val testLatency = "تست پینگ و سرعت"
    override val latencyMs: (Int) -> String = { ms -> "$ms ms" }
    override val shareQr = "کد QR"
    override val copyLink = "کپی لینک"
    override val copiedToast = "لینک کپی شد"
    override val editServer = "ویرایش"
    override val deleteServer = "حذف"
    override val protocolLabel = "پروتکل"
    override val serverNameLabel = "نام سرور"
    override val hostLabel = "آدرس سرور (Host / IP)"
    override val portLabel = "پورت (Port)"
    override val uuidOrUserLabel = "UUID / نام کاربری"
    override val passwordOrSecretLabel = "رمز عبور / Password"
    override val privateKeyLabel = "کلید خصوصی SSH (اختیاری - PEM)"
    override val transportLabel = "انتقال (Transport)"
    override val tlsEnabledLabel = "فعال‌سازی رمزنگاری TLS"
    override val sniLabel = "SNI (اختیاری)"
    override val wsHostLabel = "WebSocket Host"
    override val wsPathLabel = "WebSocket Path"
    override val cipherLabel = "الگوریتم رمزنگاری (Cipher)"
    override val allowInsecureLabel = "پذیرش گواهی ناامن (Allow Insecure)"
    override val saveButton = "ذخیره سرور"
    override val cancelButton = "انصراف"
    override val splitTunnelTitle = "ترافیک و برنامه‌ها"
    override val splitModeFull = "کل سیستم"
    override val splitModeFullDesc = "تمامی ترافیک گوشی از طریق VPN عبور می‌کند"
    override val splitModeInclude = "فقط اپ‌های منتخب"
    override val splitModeIncludeDesc = "فقط برنامه‌های تیک‌خورده از VPN استفاده می‌کنند"
    override val splitModeExclude = "دور زدن اپ‌های منتخب"
    override val splitModeExcludeDesc = "همه اپ‌ها جز موارد تیک‌خورده از VPN عبور می‌کنند"
    override val searchAppsHint = "جستجوی نام برنامه یا پکیج..."
    override val showSystemApps = "نمایش اپ‌های سیستمی"
    override val selectAll = "انتخاب همه"
    override val clearAll = "پاک کردن"
    override val appsSelectedCount: (Int) -> String = { count -> "$count برنامه انتخاب شده" }
    override val reconnectToApply = "تغییر تانل در اتصال بعدی اعمال می‌شود"
    override val settingsTitle = "تنظیمات و بهینه‌سازی"
    override val batterySectionTitle = "مدیریت مصرف باتری"
    override val batteryUltraLowTitle = "حالت مصرف فوق‌کم (پیشنهادی)"
    override val batteryUltraLowDesc = "ورودی مسدودشونده هسته لینوکس بدون بیدارباش، کاهش آپدیت نوتیفیکیشن و بستن خودکار سوکت‌های بی‌استفاده"
    override val batteryBalancedTitle = "حالت متعادل"
    override val batteryBalancedDesc = "بروزرسانی سریع‌تر آمار و نوتیفیکیشن"
    override val systemBatterySettings = "تنظیمات باتری اندروید (رفع محدودیت پس‌زمینه)"
    override val networkSectionTitle = "تنظیمات شبکه و DNS"
    override val dnsPrimaryLabel = "DNS اصلی"
    override val dnsSecondaryLabel = "DNS جایگزین"
    override val mtuLabel = "MTU تونل"
    override val autoCheckIpLabel = "بررسی خودکار IP و پرچم پس از اتصال"
    override val languageSectionTitle = "زبان برنامه (Language)"
    override val logsTitle = "گزارش اتصال (Logs)"
    override val clearLogs = "پاک کردن"
    override val noLogsYet = "گزارشی ثبت نشده است"
    override val importedCountMsg: (Int) -> String = { c -> "$c سرور با موفقیت اضافه شد" }
    override val importFailedMsg = "لینک یا فایل معتبر پیدا نشد"
    override val exportConfigs = "خروجی (Export)"
    override val importConfigs = "ورود (Import)"
    override val exportDialogTitle = "خروجی رمزنگاری‌شده کانفیگ‌ها"
    override val exportDialogDesc: (Int) -> String = { count -> "تعداد $count سرور ذخیره می‌شود. در فایل خروجی فقط پسوردها و کلیدهای خصوصی با AES-256 رمزنگاری می‌شوند و سایر تنظیمات خوانا می‌ماند." }
    override val encryptionPasswordLabel = "رمز عبور برای قفل کردن پسوردها"
    override val encryptionPasswordHint = "حداقل ۴ کاراکتر وارد کنید"
    override val passwordTooShortError = "رمز عبور باید حداقل ۴ کاراکتر باشد"
    override val saveToFileButton = "ذخیره فایل (.json)"
    override val copyJsonButton = "کپی متن خروجی"
    override val exportSavedToast = "فایل خروجی با پسوردهای رمزنگاری‌شده ذخیره شد"
    override val exportCopiedToast = "متن خروجی با پسوردهای رمزنگاری‌شده کپی شد"
    override val noServersToExportMsg = "سروری برای خروجی گرفتن وجود ندارد"
    override val importDialogSubtitle = "فایل بکاپ (.json) انتخاب کنید یا لینک/متن کانفیگ را جایگذاری نمایید:"
    override val chooseFileButton = "انتخاب فایل بکاپ"
    override val decryptionPasswordLabel = "رمز رمزگشایی پسوردها"
    override val decryptionPasswordRequired = "این فایل دارای پسوردهای رمزنگاری‌شده است؛ رمز را وارد کنید"
    override val wrongDecryptionPasswordMsg = "رمز رمزگشایی اشتباه است یا فایل خراب شده است"
    override val invalidBackupFileMsg = "فرمت فایل بکاپ یا لینک‌های ورودی معتبر نیست"
    override val showPasswordDesc = "نمایش رمز"
    override val hidePasswordDesc = "مخفی کردن رمز"
    override val themeSectionTitle = "ظاهر و پس‌زمینه برنامه"
    override val themeSystem = "خودکار (سیستم)"
    override val themeDark = "دارک (تیره)"
    override val themeLight = "معمولی (روشن)"
    override val switchThemeDesc = "تغییر تم تیره و روشن"
    override val splashSubtitle = "امنیت چندپروتکله • مصرف باتری فوق‌کم"
    override val trafficTabLive = "ترافیک زنده"
    override val trafficTabRules = "تنظیمات تانل"
    override val trafficFilterAll = "همه"
    override val trafficFilterPassed = "عبور موفق"
    override val trafficFilterFailed = "ناموفق"
    override val trafficStatusPassed = "عبور"
    override val trafficStatusFailed = "ناموفق"
    override val trafficEmptyDisconnected = "پس از اتصال به VPN، ترافیک لحظه‌ای برنامه‌ها (عبور موفق یا ناموفق) اینجا نمایش داده می‌شود."
    override val trafficEmptyWaiting = "متصل است — در انتظار ترافیک برنامه‌ها..."
    override val trafficSystemAppLabel = "شبکه سیستم / هسته"
    override val trafficSummaryCounts: (Int, Int) -> String = { apps, failed -> "$apps اپ فعال • $failed ناموفق" }
    override val crashlyticsSectionTitle = "گزارش خطا و پایداری (Crashlytics)"
    override val crashlyticsEnabledLabel = "ارسال خودکار گزارش کرش و خطاهای VPN"
    override val crashlyticsEnabledDesc = "کرش‌ها و خطاهای بحرانی تانل بدون ذخیره رمزها یا اطلاعات شخصی به Firebase Crashlytics ارسال می‌شود"
    override val pasteClipboardQuickButton = "پیست کانفیگ"
    override val clipboardEmptyMsg = "کلیپ‌بورد خالی است"
    override val clipboardNoValidConfigMsg = "کانفیگ معتبری (vmess, vless, trojan, ss, ssh, socks) در کلیپ‌بورد یافت نشد"
    override val importedWithTypesMsg: (Int, String) -> String = { count, types -> "$count سرور اضافه شد ($types)" }
    override val subscriptionsButton = "سابسکریپشن"
    override val subscriptionsCountButton: (Int) -> String = { count -> "سابسکریپشن ($count)" }
    override val subscriptionsTitle = "مدیریت سابسکریپشن‌ها"
    override val addSubscriptionTitle = "افزودن سابسکریپشن جدید"
    override val editSubscriptionTitle = "ویرایش سابسکریپشن"
    override val addAndSyncButton = "افزودن و بروزرسانی"
    override val saveAndSyncButton = "ذخیره و بروزرسانی"
    override val updateAllSubscriptions = "بروزرسانی همه"
    override val updateSubscription = "بروزرسانی"
    override val deleteSubscription = "حذف سابسکریپشن"
    override val subscriptionNameLabel = "نام (اختیاری)"
    override val subscriptionNameHint = "مثلاً سرورهای V2Ray من"
    override val subscriptionUrlLabel = "لینک سابسکریپشن (https://...)"
    override val subscriptionUrlHint = "لینک اشتراک vmess / vless را وارد یا پیست کنید"
    override val subscriptionServersCount: (Int) -> String = { count -> "$count سرور" }
    override val subscriptionUpdatedSuccess: (String, Int) -> String = { name, count -> "$name بروزرسانی شد ($count سرور)" }
    override val subscriptionUpdateAllResult: (Int, Int) -> String = { ok, total -> "$ok از $total سابسکریپشن بروزرسانی شد" }
    override val noSubscriptionsTitle = "هنوز سابسکریپشنی ثبت نشده است"
    override val noSubscriptionsSubtitle = "لینک اشتراک (Subscription URL) شامل سرورهای VMess، VLESS، Trojan یا Shadowsocks را اینجا اضافه کنید تا همیشه با یک لمس آپدیت شوند."
    override val subscriptionUrlDetectedMsg = "لینک سابسکریپشن شناسایی شد — آماده افزودن و همگام‌سازی"
    override val updateRequiredTitle = "آپدیت اجباری نسخه جدید"
    override val updateAvailableTitle = "نسخه جدید منتشر شد"
    override val updateForceDesc = "برای ادامه استفاده از Baldr VPN باید آخرین نسخه را دانلود و نصب کنید."
    override val updateOptionalDesc = "نسخه جدیدی از Baldr VPN آماده دانلود است."
    override val updateDownloadButton = "دانلود نسخه جدید"
    override val updateLaterButton = "بعداً"
    override val updateInvalidUrlMsg = "امکان باز کردن لینک آپدیت وجود ندارد"
    override val firebaseServicesSectionTitle = "سرویس‌های فایربیس و آپدیت"
    override val checkUpdatesButton = "بررسی آپدیت (Remote Config)"
    override val noUpdateAvailableToast = "برنامه بروز است (آپدیتی در Remote Config فعال نیست)"
    override val testAnalyticsButton = "ارسال رویداد تست به Analytics"
    override val analyticsTestSentToast = "رویداد تست به Firebase Analytics و In-App Messaging ارسال شد"
    override val analyticsTestFailedToast = "ارسال رویداد تست با خطا مواجه شد"
    override val copyFirebaseIdsButton = "کپی شناسه تست (FID / FCM Token)"
    override val firebaseIdsCopiedToast = "شناسه FID و توکن FCM در کلیپ‌بورد کپی شد"
    override val firebaseIdsUnavailableToast = "در حال دریافت شناسه از فایربیس — لحظه‌ای بعد دوباره امتحان کنید"
    override val remoteConfigFetchFailedToast = "خطا در ارتباط با Firebase Remote Config — اتصال اینترنت را بررسی کنید"
    override val shortcutsSectionTitle = "دسترسی سریع (ویجت و نوتیفیکیشن پنل)"
    override val pinWidgetButton = "افزودن ویجت کوچک به صفحه اصلی"
    override val pinWidgetNotSupportedToast = "لانچر گوشی شما پین خودکار ندارد؛ از لیست ویجت‌های صفحه اصلی Baldr Quick Connect را اضافه کنید"
    override val addQuickTileButton = "افزودن دکمه به نوتیفیکیشن پنل (Quick Tile)"
    override val quickTileAddedToast = "دکمه Baldr VPN به پنل نوتیفیکیشن اضافه شد"
    override val quickTileManualHintToast = "پنل نوتیفیکیشن را پایین بکشید، دکمه ویرایش (مداد) را بزنید و Baldr VPN را اضافه کنید"
    override val firebaseStatusLabel = "وضعیت اتصال فایربیس"
    override val pingAllButton = "پینگ همه"
    override val stopPingButton = "توقف پینگ"
    override val pingingProgress: (Int, Int) -> String = { done, total -> "در حال سنجش پینگ سرورها: $done از $total" }
    override val sortByFastestChip = "مرتب‌سازی سریع‌ترین"
    override val fastestBadge = "سریع‌ترین"
    override val pingTimeoutLabel = "تایم‌اوت"
    override val tapToPingLabel = "تست پینگ"
    override val speedTestActionDesc = "تست سرعت دانلود"
    override val lowBatteryModeTitle = "عملکرد باتری کم (زیر ۲۰٪)"
    override val lowBatteryModeDesc = "با افت باتری زیر ۲۰٪ ترافیک پرمصرف پس‌زمینه VPN، تست دانلود و بررسی خودکار IP متوقف شده و فرکانس پینگ ۵ برابر کاهش می‌یابد"
    override val lowBatteryActiveBadge: (Int) -> String = { pct -> "فعال • باتری $pct٪" }
    override val lowBatteryStandbyBadge: (Int) -> String = { pct -> if (pct >= 0) "آماده‌باش • باتری $pct٪" else "آماده‌باش (<۲۰٪)" }
    override val lowBatteryBannerTitle: (Int) -> String = { pct -> "حالت باتری کم فعال است ($pct٪)" }
    override val lowBatteryBannerSubtitle: (Int) -> String = { paused ->
        if (paused > 0) {
            "ترافیک پرمصرف پس‌زمینه متوقف شد ($paused مورد) • فرکانس پینگ کاهش یافت"
        } else {
            "ترافیک پرمصرف پس‌زمینه متوقف و فرکانس پینگ کاهش یافت"
        }
    }
    override val lowBatteryServersBanner: (Int) -> String = { pct -> "حالت باتری کم ($pct٪): پینگ خودکار و تست سرعت گروهی متوقف شد؛ پینگ دستی با سرعت کم انجام می‌شود." }
    override val lowBatteryBenchmarkPausedToast = "تست سرعت گروهی در حالت باتری کم (<۲۰٪) برای صرفه‌جویی متوقف است"
    override val dualVpnTitle = "اتصال دوگانه VPN (فعال / آماده)"
    override val dualVpnActiveLabel = "فعال"
    override val dualVpnStandbyLabel = "آماده تعویض"
    override val dualVpnReadyLabel = "متصل"
    override val dualVpnWarmingLabel = "بررسی..."
    override val switchVpnNowButton = "تعویض VPN"
    override val multiTunnelSwitchesCount: (Int) -> String = { n -> "$n تعویض" }
    override val systemAlwaysOnVpnDesc = "تنظیمات VPN اندروید برای فعال‌سازی Always-On سیستمی"
    override val serversTabMyServers = "سرورهای من"
    override val serversTabFreeProviders = "پروکسی رایگان"
    override val freeProvidersBannerTitle = "ارائه‌دهندگان رایگان پروکسی و V2Ray"
    override val freeProvidersBannerSubtitle = "هر ارائه‌دهنده را انتخاب کنید تا سرورهای فعال آن پینگ گرفته شوند و به‌صورت خودکار به کم‌پینگ‌ترین سرور متصل شوید."
    override val freeProviderPingConnectButton = "پینگ و اتصال"
    override val freeProviderFetching = "در حال دریافت لیست سرورها..."
    override val freeProviderPingingProgress: (Int, Int) -> String = { done, total -> "در حال پینگ: $done از $total" }
    override val freeProviderVerifyingBest = "در حال بررسی و اتصال به بهترین سرور..."
    override val freeProviderReachableCount: (Int) -> String = { n -> "$n فعال" }
    override val freeProviderConnectedToast: (String, Int) -> String = { name, ms -> "اتصال به بهترین سرور $name ($ms ms)" }
    override val freeProviderNoWorkingServerMsg: (String) -> String = { name -> "سرور فعالی از $name پاسخ نداد. ارائه‌دهنده دیگری را امتحان کنید." }
    override val freeProviderIranBadge = "مناسب ایران"
    override val freeProviderFilterAll = "همه"
    override val freeProviderFilterIran = "مناسب ایران (V2Ray)"
    override val freeProviderFilterProxy = "پروکسی (SOCKS/HTTP)"
    override val freeProviderUpdatedDateLabel: (String) -> String = { date -> "آپدیت: $date" }
    override val freeProviderDefaultDateLabel: (String) -> String = { date -> "پیش‌فرض: $date" }
    override val freeProviderCachedCount: (Int) -> String = { n -> "$n سرور آماده" }
    override val freeProviderBackgroundSyncBanner: (String, Int, Int) -> String = { name, done, total -> "VPN متصل است · بروزرسانی تدریجی $name ($done از $total)..." }
    override val freeProviderUsingOfflineFallback = "در حال پینگ سرورهای پیش‌فرض ذخیره شده..."
    override val freeProviderUpdatingNow = "در حال بروزرسانی لیست..."
    override val freeProvidersDisclaimerTitle = "سلب مسئولیت امنیتی پروایدرهای رایگان"
    override val freeProvidersDisclaimerBody = "سرورهای این بخش توسط ارائه‌دهندگان عمومی و مستقل ارائه می‌شوند و مسئولیت امنیت، حریم خصوصی و حفاظت از اطلاعات روی آن‌ها تماماً بر عهده خود ارائه‌دهنده (پروایدر) است. لطفاً برای امور حساس بانکی از سرورهای رایگان عمومی استفاده نکنید."
    override val serverSpeedDetailsTitle = "جزئیات سرور و سرعت"
    override val quickWidgetShortButton = "ویجت اتصال سریع"
    override val controlCenterShortButton = "دکمه کنترل سنتر"
    // Feature 1: Smart Routing (Iran & LAN Bypass)
    override val smartRoutingSectionTitle = "مسیریابی هوشمند (دور زدن ایران و شبکه داخلی)"
    override val bypassIranTitle = "عبور مستقیم سایت‌ها و آی‌پی‌های ایران"
    override val bypassIranDesc = "دامنه‌های .ir، بانک‌ها و سرویس‌های داخلی ایران بدون VPN و با سرعت کامل باز شوند"
    override val bypassLanTitle = "دور زدن شبکه داخلی (LAN)"
    override val bypassLanDesc = "آی‌پی‌های محلی (روتر، پرینتر، مودم 192.168.x.x) مستقیم بدون تونل باز شوند"
    override val customBypassDomainsLabel = "دامنه‌های دلخواه بدون VPN (جداشده با ویرگول)"
    override val customBypassDomainsHint = "مثلاً: digikala.com, snapp.ir, bankmellat.ir"
    override val bypassedLiveBadge: (Int) -> String = { n -> "عبور مستقیم: $n" }
    // Feature 2: Smart Auto-Connect & Auto-Failover
    override val smartAutoConnectButton = "اتصال هوشمند به سریع‌ترین"
    override val smartAutoConnectingLabel = "در حال یافتن بهترین سرور..."
    override val smartAutoConnectNoServerToast = "سرور فعالی پیدا نشد؛ ابتدا سرور اضافه کنید یا از پروکسی رایگان استفاده نمایید"
    override val smartAutoConnectedToast: (String, Int) -> String = { name, ms ->
        if (ms > 0) "اتصال هوشمند به $name ($ms ms)" else "اتصال هوشمند به $name"
    }
    override val smartFailoverTitle = "تعویض هوشمند سرور (Auto-Failover)"
    override val smartFailoverDesc = "در صورت افت یا قطع سرور فعلی، خودکار به بهترین سرور پشتیبان بدون قطع تونل سوئیچ شود"
    // Feature 3: Custom DNS / DoH Presets & AdBlock DNS
    override val dnsPresetLabel = "پروفایل آماده DNS / DoH"
    override val adBlockDnsTitle = "مسدودسازی تبلیغات و ردیاب‌ها (AdBlock DNS)"
    override val adBlockDnsDesc = "فیلتر خودکار دامنه‌های تبلیغاتی و ردیاب در سطح DNS درون تونل"
    override val adsBlockedLiveBadge: (Int) -> String = { n -> "تبلیغ مسدودشده: $n" }
    override val customDohUrlLabel = "آدرس DNS-over-HTTPS (اختیاری)"
    override val customDohUrlHint = "https://dns.adguard-dns.com/dns-query"
    // Feature 4: Search, Sort, Favorites & Server Cleaner
    override val searchServersHint = "جستجوی نام، آدرس یا کشور..."
    override val filterFavoritesChip = "محبوب‌ها"
    override val sortDefaultChip = "پیش‌فرض"
    override val sortPingChip = "کمترین پینگ"
    override val sortCountryChip = "کشور"
    override val favoriteToggleDesc = "افزودن یا حذف از محبوب‌ها"
    override val cleanServersButton = "پاکسازی"
    override val cleanServersDialogTitle = "پاکسازی سرورهای ازکارافتاده و تکراری"
    override val cleanServersDialogBody: (Int, Int) -> String = { dead, dup ->
        "تعداد $dead سرور ازکارافتاده (تایم‌اوت) و $dup سرور تکراری پیدا شد. حذف شوند؟"
    }
    override val cleanServersConfirmButton = "حذف همه"
    override val cleanServersNothingToast = "سرور ازکارافتاده یا تکراری یافت نشد"
    override val cleanServersDoneToast: (Int) -> String = { n -> "$n سرور ازکارافتاده/تکراری پاکسازی شد" }
    // Feature 5: Live Camera QR Scanner
    override val scanQrButton = "اسکن QR"
    override val qrScannerTitle = "اسکن کد QR کانفیگ یا سابسکریپشن"
    override val qrScannerSubtitle = "دوربین را روی کد QR لینک vless، vmess، trojan، ss یا سابسکریپشن نگه دارید"
    override val qrCameraPermissionNeeded = "برای اسکن کد QR نیاز به دسترسی دوربین است"
    override val qrGrantPermissionButton = "اجازه دسترسی دوربین"
    override val qrTorchToggleDesc = "روشن/خاموش کردن فلش"
    // Feature 6: Data Usage History & Enhanced Live Logs
    override val dataUsageSectionTitle = "آمار مصرف اینترنت (روزانه و ماهانه)"
    override val dataUsageToday = "امروز"
    override val dataUsageWeek = "۷ روز اخیر"
    override val dataUsageMonth = "۳۰ روز اخیر"
    override val dataUsageLast7Days = "نمودار مصرف ۷ روز گذشته"
    override val dataUsageResetButton = "صفر کردن آمار"
    override val logFilterAll = "همه"
    override val logFilterErrors = "خطاها"
    override val logFilterRouting = "مسیریابی"
    override val logFilterFailover = "سوئیچ خودکار"
    override val logFilterAdBlock = "ضدتبلیغ"
    override val copyLogsButton = "کپی لاگ"
    override val logsCopiedToast = "گزارش اتصال در کلیپ‌بورد کپی شد"
}

object EnglishStrings : AppStrings {
    override val appTitle = "Baldr VPN"
    override val navDashboard = "Connect"
    override val navServers = "Servers"
    override val navSplitTunnel = "Traffic"
    override val navSettings = "Settings"
    override val statusDisconnected = "Disconnected"
    override val statusConnecting = "Connecting..."
    override val statusConnected = "Connected & Protected"
    override val statusReconnecting = "Reconnecting..."
    override val statusFirewallLocked = "Firewall Lock Active"
    override val statusDisconnecting = "Disconnecting..."
    override val statusError = "Connection Error"
    override val tapToConnect = "Tap to Connect"
    override val tapToDisconnect = "Tap to Disconnect"
    override val tapToUnlockFirewall = "Tap to Disconnect & Release Firewall"
    override val selectServerPrompt = "Add or select a server first"
    override val activeProfileLabel = "Active Server"
    override val noProfileSelected = "No server selected"
    override val protectionCardTitle = "Connection Guard"
    override val killSwitchTitle = "Firewall (Kill Switch)"
    override val killSwitchDesc = "Keep traffic blocked if VPN disconnects unexpectedly"
    override val autoReconnectTitle = "Auto-Reconnect"
    override val autoReconnectDesc = "Automatically reconnect if tunnel drops or network switches"
    override val firewallLockedBanner = "Firewall active: non-VPN traffic is blocked until reconnected."
    override val systemAlwaysOnVpn = "System Always-On VPN"
    override val exitLocationTitle = "VPN Exit IP & Location"
    override val exitIpLoading = "Resolving exit IP & country..."
    override val exitIpPlaceholder = "Exit IP and country flag appear once connected"
    override val refreshIp = "Refresh IP"
    override val ispLabel = "Provider"
    override val liveSpeedTitle = "Live Speed & Traffic"
    override val downloadSpeed = "Download Speed"
    override val uploadSpeed = "Upload Speed"
    override val totalDownloaded = "Downloaded"
    override val totalUploaded = "Uploaded"
    override val sessionDuration = "Duration"
    override val activeStreams = "Streams"
    override val tunnelSummaryFull = "Full System Tunnel"
    override val tunnelSummaryInclude: (Int) -> String = { count -> "Only $count Selected Apps" }
    override val tunnelSummaryExclude: (Int) -> String = { count -> "All Except $count Apps" }
    override val batteryLowPowerBadge = "Ultra-Low Battery"
    override val batteryBalancedBadge = "Balanced"
    override val serversTitle = "Server Profiles"
    override val filterAll = "All"
    override val filterMulti = "Load Balance"
    override val testAllConnections = "Test All Servers"
    override val stopTest = "Stop Test"
    override val testingProgress: (Int, Int) -> String = { done, total -> "Testing ping & download: $done of $total" }
    override val testingNow = "Testing..."
    override val testFailed = "Failed"
    override val testDialogTitle = "Test All Servers"
    override val testDialogMessage = "Each server will be pinged and tested with a live file download to measure real speed."
    override val autoConnectFastestCheck = "Auto-connect to the fastest server when test finishes"
    override val startTestButton = "Start Test"
    override val autoConnectedFastestMsg: (String) -> String = { name -> "Auto-connecting to fastest server: $name" }
    override val createLoadBalance = "Multi-Server Load Balance"
    override val loadBalanceStrategyLabel = "Multi-Mode Strategy"
    override val strategyFailover = "Dual-VPN Auto-Switch (Active + Standby)"
    override val strategyRoundRobin = "Concurrent Round-Robin"
    override val strategyLeastLatency = "Least Latency + Balance"
    override val strategyRandom = "Parallel Random"
    override val selectMembersHint = "Select 2+ servers (SOCKS / HTTP / V2Ray / SSH) to combine:"
    override val membersCountBadge: (Int) -> String = { count -> "$count servers combined" }
    override val needTwoMembersMsg = "Select at least 2 servers to create a Load Balance profile"
    override val addServer = "Add Manual"
    override val importFromLink = "Import URI"
    override val pasteConfigHint = "Paste vmess://, vless://, trojan://, ss://, socks5://, socks4://, http:// or ssh:// links"
    override val importFromClipboard = "Paste Clipboard"
    override val importButton = "Import"
    override val noServersTitle = "No Server Profiles Yet"
    override val noServersSubtitle = "Tap + to add or import V2Ray, SSH, SOCKS4/5, or HTTP Proxy servers"
    override val testLatency = "Ping & Speed Test"
    override val latencyMs: (Int) -> String = { ms -> "$ms ms" }
    override val shareQr = "QR Code"
    override val copyLink = "Copy URI"
    override val copiedToast = "URI copied"
    override val editServer = "Edit"
    override val deleteServer = "Delete"
    override val protocolLabel = "Protocol"
    override val serverNameLabel = "Profile Name"
    override val hostLabel = "Server Host / IP"
    override val portLabel = "Port"
    override val uuidOrUserLabel = "UUID / Username"
    override val passwordOrSecretLabel = "Password / Secret"
    override val privateKeyLabel = "SSH Private Key (Optional PEM)"
    override val transportLabel = "Transport"
    override val tlsEnabledLabel = "Enable TLS Encryption"
    override val sniLabel = "SNI (Optional)"
    override val wsHostLabel = "WebSocket Host"
    override val wsPathLabel = "WebSocket Path"
    override val cipherLabel = "Cipher"
    override val allowInsecureLabel = "Allow Insecure TLS Cert"
    override val saveButton = "Save Server"
    override val cancelButton = "Cancel"
    override val splitTunnelTitle = "App Traffic & Routing"
    override val splitModeFull = "Full System"
    override val splitModeFullDesc = "Route all device traffic through the VPN tunnel"
    override val splitModeInclude = "Only Selected"
    override val splitModeIncludeDesc = "Only checked apps use the VPN tunnel"
    override val splitModeExclude = "Bypass Selected"
    override val splitModeExcludeDesc = "All apps except checked ones use the VPN"
    override val searchAppsHint = "Search app name or package..."
    override val showSystemApps = "Show system apps"
    override val selectAll = "Select All"
    override val clearAll = "Clear"
    override val appsSelectedCount: (Int) -> String = { count -> "$count apps selected" }
    override val reconnectToApply = "Reconnect VPN to apply routing changes"
    override val settingsTitle = "Settings & Power"
    override val batterySectionTitle = "Battery Optimization"
    override val batteryUltraLowTitle = "Ultra-Low Power Mode (Recommended)"
    override val batteryUltraLowDesc = "Kernel-blocking TUN I/O, batched notification updates, and automatic idle socket pruning"
    override val batteryBalancedTitle = "Balanced Mode"
    override val batteryBalancedDesc = "Faster notification telemetry updates"
    override val systemBatterySettings = "Android Battery Optimization Settings"
    override val networkSectionTitle = "Network & DNS"
    override val dnsPrimaryLabel = "Primary DNS"
    override val dnsSecondaryLabel = "Secondary DNS"
    override val mtuLabel = "Tunnel MTU"
    override val autoCheckIpLabel = "Auto-check Exit IP & Flag on connect"
    override val languageSectionTitle = "App Language"
    override val logsTitle = "Connection Diagnostics"
    override val clearLogs = "Clear"
    override val noLogsYet = "No diagnostic logs yet"
    override val importedCountMsg: (Int) -> String = { c -> "Imported $c server(s)" }
    override val importFailedMsg = "No valid server URI or backup found"
    override val exportConfigs = "Export"
    override val importConfigs = "Import"
    override val exportDialogTitle = "Export Configs (Encrypted Passwords)"
    override val exportDialogDesc: (Int) -> String = { count -> "Exporting $count server(s). Only passwords & private keys are encrypted with AES-256-GCM; server hosts and settings remain readable JSON." }
    override val encryptionPasswordLabel = "Encryption password for secrets"
    override val encryptionPasswordHint = "Enter at least 4 characters"
    override val passwordTooShortError = "Password must be at least 4 characters"
    override val saveToFileButton = "Save File (.json)"
    override val copyJsonButton = "Copy JSON"
    override val exportSavedToast = "Backup saved with encrypted passwords"
    override val exportCopiedToast = "Encrypted backup copied to clipboard"
    override val noServersToExportMsg = "No servers available to export"
    override val importDialogSubtitle = "Choose a backup file (.json) or paste config links / backup JSON below:"
    override val chooseFileButton = "Choose Backup File"
    override val decryptionPasswordLabel = "Password to decrypt secrets"
    override val decryptionPasswordRequired = "Encrypted backup detected — enter password to unlock secrets"
    override val wrongDecryptionPasswordMsg = "Wrong decryption password or corrupted backup"
    override val invalidBackupFileMsg = "Invalid backup format or server URI"
    override val showPasswordDesc = "Show password"
    override val hidePasswordDesc = "Hide password"
    override val themeSectionTitle = "Theme & Background"
    override val themeSystem = "System Auto"
    override val themeDark = "Dark"
    override val themeLight = "Light"
    override val switchThemeDesc = "Switch dark and light theme"
    override val splashSubtitle = "Multi-Protocol Shield • Ultra-Low Power"
    override val trafficTabLive = "Live Traffic"
    override val trafficTabRules = "Tunnel Rules"
    override val trafficFilterAll = "All"
    override val trafficFilterPassed = "Passed"
    override val trafficFilterFailed = "Failed"
    override val trafficStatusPassed = "Passed"
    override val trafficStatusFailed = "Failed"
    override val trafficEmptyDisconnected = "Connect VPN to monitor live per-app passed and failed traffic here."
    override val trafficEmptyWaiting = "Connected — waiting for app traffic..."
    override val trafficSystemAppLabel = "System Network"
    override val trafficSummaryCounts: (Int, Int) -> String = { apps, failed -> "$apps active apps • $failed failed" }
    override val crashlyticsSectionTitle = "Crash & Diagnostics (Crashlytics)"
    override val crashlyticsEnabledLabel = "Auto-report crashes & VPN errors"
    override val crashlyticsEnabledDesc = "Sends crashes and non-fatal tunnel errors to Firebase Crashlytics without credentials or personal data"
    override val pasteClipboardQuickButton = "Paste Config"
    override val clipboardEmptyMsg = "Clipboard is empty"
    override val clipboardNoValidConfigMsg = "No valid vmess, vless, trojan, ss, ssh, or socks config found in clipboard"
    override val importedWithTypesMsg: (Int, String) -> String = { count, types -> "Added $count server(s) ($types)" }
    override val subscriptionsButton = "Subscriptions"
    override val subscriptionsCountButton: (Int) -> String = { count -> "Subs ($count)" }
    override val subscriptionsTitle = "Subscriptions"
    override val addSubscriptionTitle = "Add Subscription"
    override val editSubscriptionTitle = "Edit Subscription"
    override val addAndSyncButton = "Add & Sync"
    override val saveAndSyncButton = "Save & Sync"
    override val updateAllSubscriptions = "Update All"
    override val updateSubscription = "Update"
    override val deleteSubscription = "Delete subscription"
    override val subscriptionNameLabel = "Name (Optional)"
    override val subscriptionNameHint = "e.g. My V2Ray Feed"
    override val subscriptionUrlLabel = "Subscription URL (https://...)"
    override val subscriptionUrlHint = "Paste VMess / VLESS subscription link"
    override val subscriptionServersCount: (Int) -> String = { count -> "$count servers" }
    override val subscriptionUpdatedSuccess: (String, Int) -> String = { name, count -> "$name synced ($count servers)" }
    override val subscriptionUpdateAllResult: (Int, Int) -> String = { ok, total -> "Updated $ok of $total subscription(s)" }
    override val noSubscriptionsTitle = "No Subscriptions Yet"
    override val noSubscriptionsSubtitle = "Add a VMess / VLESS / Trojan / Shadowsocks subscription link to sync and refresh servers automatically."
    override val subscriptionUrlDetectedMsg = "Subscription URL detected — tap Add & Sync to import servers"
    override val updateRequiredTitle = "Required Update Available"
    override val updateAvailableTitle = "New Version Available"
    override val updateForceDesc = "A mandatory update is required to continue using Baldr VPN. Please download and install the latest version."
    override val updateOptionalDesc = "A newer version of Baldr VPN is ready to download."
    override val updateDownloadButton = "Download Update"
    override val updateLaterButton = "Later"
    override val updateInvalidUrlMsg = "Unable to open update link"
    override val firebaseServicesSectionTitle = "Firebase Services & Updates"
    override val checkUpdatesButton = "Check for Updates (Remote Config)"
    override val noUpdateAvailableToast = "You are up to date (no active update in Remote Config)"
    override val testAnalyticsButton = "Send Test Event to Analytics"
    override val analyticsTestSentToast = "Test event sent to Firebase Analytics & In-App Messaging"
    override val analyticsTestFailedToast = "Failed to send Analytics test event"
    override val copyFirebaseIdsButton = "Copy Test IDs (FID / FCM Token)"
    override val firebaseIdsCopiedToast = "Firebase Installation ID & FCM Token copied"
    override val firebaseIdsUnavailableToast = "Fetching Firebase IDs — please try again in a moment"
    override val remoteConfigFetchFailedToast = "Unable to reach Firebase Remote Config — check connection"
    override val shortcutsSectionTitle = "Quick Access (Widget & Notification Tile)"
    override val pinWidgetButton = "Add Compact Home Screen Widget"
    override val pinWidgetNotSupportedToast = "Add 'Baldr Quick Connect' manually from your launcher's Widgets menu"
    override val addQuickTileButton = "Add Notification Panel Button (Quick Tile)"
    override val quickTileAddedToast = "Baldr VPN Quick Tile added to notification panel"
    override val quickTileManualHintToast = "Swipe down the notification panel, tap Edit, and drag Baldr VPN into active tiles"
    override val firebaseStatusLabel = "Firebase Connection Status"
    override val pingAllButton = "Ping All"
    override val stopPingButton = "Stop Ping"
    override val pingingProgress: (Int, Int) -> String = { done, total -> "Pinging servers: $done of $total" }
    override val sortByFastestChip = "Fastest First"
    override val fastestBadge = "Fastest"
    override val pingTimeoutLabel = "Timeout"
    override val tapToPingLabel = "Ping"
    override val speedTestActionDesc = "Speed test"
    override val lowBatteryModeTitle = "Low-Battery Operation (<20%)"
    override val lowBatteryModeDesc = "Automatically pauses high-resource background VPN traffic & bulk benchmarks and reduces ping/keepalive frequency when battery drops below 20%"
    override val lowBatteryActiveBadge: (Int) -> String = { pct -> "Active • $pct%" }
    override val lowBatteryStandbyBadge: (Int) -> String = { pct -> if (pct >= 0) "Standby • $pct%" else "Standby (<20%)" }
    override val lowBatteryBannerTitle: (Int) -> String = { pct -> "Low-Battery Operation Active ($pct%)" }
    override val lowBatteryBannerSubtitle: (Int) -> String = { paused ->
        if (paused > 0) {
            "Paused $paused background task(s) • Ping & telemetry frequency reduced"
        } else {
            "High-resource background VPN traffic paused • Ping frequency reduced"
        }
    }
    override val lowBatteryServersBanner: (Int) -> String = { pct -> "Low-Battery Operation ($pct%): Auto-ping & bulk download tests are paused. Manual pings run sequentially at reduced frequency." }
    override val lowBatteryBenchmarkPausedToast = "Bulk download speed tests are paused while battery is below 20%"
    override val dualVpnTitle = "Dual-VPN Active / Standby"
    override val dualVpnActiveLabel = "Active"
    override val dualVpnStandbyLabel = "Standby"
    override val dualVpnReadyLabel = "Ready"
    override val dualVpnWarmingLabel = "Checking"
    override val switchVpnNowButton = "Switch VPN"
    override val multiTunnelSwitchesCount: (Int) -> String = { n -> "$n switch(es)" }
    override val systemAlwaysOnVpnDesc = "Open Android VPN Settings to enable OS-level Always-On"
    override val serversTabMyServers = "My Servers"
    override val serversTabFreeProviders = "Free Proxies"
    override val freeProvidersBannerTitle = "Free Proxy & V2Ray Providers"
    override val freeProvidersBannerSubtitle = "Tap any provider to fetch live public servers, ping them automatically, and connect to the fastest working node."
    override val freeProviderPingConnectButton = "Ping & Connect"
    override val freeProviderFetching = "Fetching proxy list..."
    override val freeProviderPingingProgress: (Int, Int) -> String = { done, total -> "Pinging servers: $done / $total" }
    override val freeProviderVerifyingBest = "Verifying & connecting to fastest server..."
    override val freeProviderReachableCount: (Int) -> String = { n -> "$n live" }
    override val freeProviderConnectedToast: (String, Int) -> String = { name, ms -> "Connecting to fastest $name server ($ms ms)" }
    override val freeProviderNoWorkingServerMsg: (String) -> String = { name -> "No reachable server responded from $name. Please try another provider." }
    override val freeProviderIranBadge = "Iran-Ready"
    override val freeProviderFilterAll = "All"
    override val freeProviderFilterIran = "Iran-Ready (V2Ray)"
    override val freeProviderFilterProxy = "Proxies (SOCKS/HTTP)"
    override val freeProviderUpdatedDateLabel: (String) -> String = { date -> "Updated: $date" }
    override val freeProviderDefaultDateLabel: (String) -> String = { date -> "Bundled: $date" }
    override val freeProviderCachedCount: (Int) -> String = { n -> "$n ready" }
    override val freeProviderBackgroundSyncBanner: (String, Int, Int) -> String = { name, done, total -> "VPN active · Refreshing $name ($done/$total)..." }
    override val freeProviderUsingOfflineFallback = "Using bundled servers & pinging..."
    override val freeProviderUpdatingNow = "Updating list..."
    override val freeProvidersDisclaimerTitle = "Free Providers Security Disclaimer"
    override val freeProvidersDisclaimerBody = "Servers listed here are operated by independent public third-party providers. Security, privacy, and data handling on these servers are solely the responsibility of each provider. Avoid sensitive banking activity over public free proxies."
    override val serverSpeedDetailsTitle = "Server & Speed Details"
    override val quickWidgetShortButton = "Quick Widget"
    override val controlCenterShortButton = "Control Center"
    // Feature 1: Smart Routing (Iran & LAN Bypass)
    override val smartRoutingSectionTitle = "Smart Routing (Iran & LAN Bypass)"
    override val bypassIranTitle = "Bypass Iranian Domains & IPs"
    override val bypassIranDesc = "Open .ir sites, domestic banks, and Iranian IPs directly outside VPN"
    override val bypassLanTitle = "Bypass Local Network (LAN)"
    override val bypassLanDesc = "Keep local routers, printers, and 192.168.x.x devices direct"
    override val customBypassDomainsLabel = "Custom Bypass Domains (comma-separated)"
    override val customBypassDomainsHint = "e.g. digikala.com, snapp.ir, bankmellat.ir"
    override val bypassedLiveBadge: (Int) -> String = { n -> "Direct: $n" }
    // Feature 2: Smart Auto-Connect & Auto-Failover
    override val smartAutoConnectButton = "Smart Auto-Connect"
    override val smartAutoConnectingLabel = "Finding fastest server..."
    override val smartAutoConnectNoServerToast = "No working server found — add a server or use Free Proxies"
    override val smartAutoConnectedToast: (String, Int) -> String = { name, ms ->
        if (ms > 0) "Smart connected to $name ($ms ms)" else "Smart connected to $name"
    }
    override val smartFailoverTitle = "Smart Auto-Failover"
    override val smartFailoverDesc = "Automatically switch to the best backup server if the active node drops"
    // Feature 3: Custom DNS / DoH Presets & AdBlock DNS
    override val dnsPresetLabel = "DNS / DoH Preset"
    override val adBlockDnsTitle = "Ad & Tracker Blocker (DNS)"
    override val adBlockDnsDesc = "Block ad networks and trackers inside the tunnel DNS resolver"
    override val adsBlockedLiveBadge: (Int) -> String = { n -> "Ads Blocked: $n" }
    override val customDohUrlLabel = "Custom DNS-over-HTTPS URL (Optional)"
    override val customDohUrlHint = "https://dns.adguard-dns.com/dns-query"
    // Feature 4: Search, Sort, Favorites & Server Cleaner
    override val searchServersHint = "Search name, host, or country..."
    override val filterFavoritesChip = "Favorites"
    override val sortDefaultChip = "Default"
    override val sortPingChip = "Lowest Ping"
    override val sortCountryChip = "Country"
    override val favoriteToggleDesc = "Toggle favorite"
    override val cleanServersButton = "Clean"
    override val cleanServersDialogTitle = "Clean Dead & Duplicate Servers"
    override val cleanServersDialogBody: (Int, Int) -> String = { dead, dup ->
        "Found $dead timed-out server(s) and $dup duplicate(s). Remove them now?"
    }
    override val cleanServersConfirmButton = "Remove All"
    override val cleanServersNothingToast = "No dead or duplicate servers found"
    override val cleanServersDoneToast: (Int) -> String = { n -> "Cleaned $n dead/duplicate server(s)" }
    // Feature 5: Live Camera QR Scanner
    override val scanQrButton = "Scan QR"
    override val qrScannerTitle = "Scan Config or Subscription QR"
    override val qrScannerSubtitle = "Point camera at a vless, vmess, trojan, ss, or subscription QR code"
    override val qrCameraPermissionNeeded = "Camera permission is required to scan QR codes"
    override val qrGrantPermissionButton = "Grant Camera Permission"
    override val qrTorchToggleDesc = "Toggle flashlight"
    // Feature 6: Data Usage History & Enhanced Live Logs
    override val dataUsageSectionTitle = "Data Usage History (Daily & Monthly)"
    override val dataUsageToday = "Today"
    override val dataUsageWeek = "Last 7 Days"
    override val dataUsageMonth = "Last 30 Days"
    override val dataUsageLast7Days = "7-Day Traffic Breakdown"
    override val dataUsageResetButton = "Reset"
    override val logFilterAll = "All"
    override val logFilterErrors = "Errors"
    override val logFilterRouting = "Routing"
    override val logFilterFailover = "Failover"
    override val logFilterAdBlock = "AdBlock"
    override val copyLogsButton = "Copy Logs"
    override val logsCopiedToast = "Connection logs copied to clipboard"
}

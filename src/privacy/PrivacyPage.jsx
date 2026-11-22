import React, { useState, useEffect } from 'react';
import {
    ArrowLeft,
    Globe,
    Mail,
    Moon,
    ShieldCheck,
    Sun,
    ExternalLink,
} from 'lucide-react';

const EFFECTIVE_DATE_ZH = '2026 年 11 月 22 日';
const EFFECTIVE_DATE_EN = 'November 22, 2026';

const CONTACT_EMAIL = 'wenku8@mewx.org';
const CONTACT_MAILTO = 'mailto:wenku8@mewx.org';
const HOMEPAGE_URL = 'https://wenku8.app/';
const PRIVACY_URL = 'https://wenku8.app/privacy/';
const GITHUB_URL = 'https://github.com/MewX/light-novel-library_Wenku8_Android';

const THIRD_PARTY_ROWS = {
    zh: [
        {
            name: 'Google AdMob',
            scope: 'Android 应用（仅限支持 GMS 的设备）',
            purpose:
                '在作品详情页与发现列表页展示内联横幅广告，用于补贴服务器运营成本。可能收集广告 ID（AD_ID）、IP 地址、设备与应用信息及广告互动数据。',
            links: [
                { label: 'Google 隐私权政策', href: 'https://policies.google.com/privacy' },
                { label: 'Google 广告技术说明', href: 'https://policies.google.com/technologies/ads' },
            ],
        },
        {
            name: 'Firebase Analytics (Google Analytics for Firebase)',
            scope: 'Android 应用（仅限支持 GMS 的设备）',
            purpose:
                '统计应用功能使用情况与产品改进分析。收集假名化的使用事件（如页面浏览、作品浏览/搜索/分享/下载、阅读器开启与时长、书架操作、登录/退出状态（不含账号密码）、书评发送、备份/诊断使用情况）以及非个人身份配置属性（语言、主题、阅读排版、电子墨水模式、发行渠道）。',
            links: [
                { label: 'Firebase 隐私与安全', href: 'https://firebase.google.com/support/privacy' },
            ],
        },
        {
            name: 'Firebase Crashlytics（含 NDK）',
            scope: 'Android 应用',
            purpose:
                '收集应用崩溃堆栈、非致命异常日志、设备型号与系统版本等技术信息，以及不包含个人身份信息的诊断标签（如当前界面、语言、阅读与主题设置、当前作品/章节 ID、是否已登录的布尔值——绝不记录用户名、邮箱或密码）。',
            links: [
                { label: 'Firebase 隐私与安全', href: 'https://firebase.google.com/support/privacy' },
            ],
        },
        {
            name: 'Google Play 应用内更新',
            scope: 'Android 应用（仅限 Google Play 版）',
            purpose: '与 Google Play 通信以检测并提供应用更新。',
            links: [
                { label: 'Google 隐私权政策', href: 'https://policies.google.com/privacy' },
            ],
        },
        {
            name: 'Cloudflare',
            scope: '中继服务、分享页与官网基础设施',
            purpose: '提供边缘计算、CDN 加速、DDoS 防护、速率限制及请求运维监控。',
            links: [
                { label: 'Cloudflare 隐私政策', href: 'https://www.cloudflare.com/privacypolicy/' },
            ],
        },
        {
            name: '轻小说文库 (Wenku8)',
            scope: 'Android 应用及上游内容源',
            purpose:
                '提供小说内容、封面、插图、头像、账号认证、云端书架与社区书评功能。应用直接加载图片资源、打开注册/找回密码网页或经中继提交账号与书评请求时，相关数据将由 Wenku8 处理。',
            links: [{ label: 'wenku8.com', href: 'https://www.wenku8.com/' }],
        },
        {
            name: 'Google AdSense',
            scope: '仅限官网首页 (wenku8.app)',
            purpose:
                '在官网首页展示网页广告以补贴服务器费用，可能使用 Cookie 或设备标识符进行广告投放与效果衡量（本隐私政策页面不展示广告）。',
            links: [
                { label: 'Google 隐私权政策', href: 'https://policies.google.com/privacy' },
            ],
        },
        {
            name: 'Disqus',
            scope: '仅限官网首页留言板 (wenku8.app)',
            purpose:
                '提供网页评论功能。您在留言板交互或登录 Disqus 时，受 Disqus 隐私政策约束（本应用及本隐私政策页面不加载 Disqus）。',
            links: [
                {
                    label: 'Disqus 隐私政策',
                    href: 'https://help.disqus.com/en/articles/1717103-disqus-privacy-policy',
                },
            ],
        },
    ],
    en: [
        {
            name: 'Google AdMob',
            scope: 'Android App (GMS devices only)',
            purpose:
                'Displays inline adaptive banner ads on Novel Detail and Discovery screens to help cover server costs. May collect Advertising ID (AD_ID), IP address, device/app info, and ad interaction data.',
            links: [
                { label: 'Google Privacy Policy', href: 'https://policies.google.com/privacy' },
                { label: 'Google Ads Info', href: 'https://policies.google.com/technologies/ads' },
            ],
        },
        {
            name: 'Firebase Analytics (Google Analytics for Firebase)',
            scope: 'Android App (GMS devices only)',
            purpose:
                'Helps us understand feature usage and improve the App. Collects pseudonymous usage events (screen views, novel views/searches/shares/downloads, reader sessions and duration, bookshelf actions, login/logout outcomes without credentials, review posts, backup/diagnostics usage) and non-PII app preferences (language, theme, reader layout, e-ink mode, distribution channel).',
            links: [
                {
                    label: 'Firebase Privacy & Security',
                    href: 'https://firebase.google.com/support/privacy',
                },
            ],
        },
        {
            name: 'Firebase Crashlytics (incl. NDK)',
            scope: 'Android App',
            purpose:
                'Collects crash stack traces, non-fatal error logs, device/OS technical metadata, and non-PII diagnostic keys (screen name, language, reader/theme settings, active novel/chapter IDs, and a boolean logged-in flag—never usernames, emails, or passwords).',
            links: [
                {
                    label: 'Firebase Privacy & Security',
                    href: 'https://firebase.google.com/support/privacy',
                },
            ],
        },
        {
            name: 'Google Play In-App Updates',
            scope: 'Android App (Google Play edition only)',
            purpose: 'Communicates with Google Play to check for and deliver app updates.',
            links: [
                { label: 'Google Privacy Policy', href: 'https://policies.google.com/privacy' },
            ],
        },
        {
            name: 'Cloudflare',
            scope: 'Relay, Share & Website Infrastructure',
            purpose:
                'Provides edge hosting, CDN caching, DDoS protection, rate limiting, and operational telemetry.',
            links: [
                {
                    label: 'Cloudflare Privacy Policy',
                    href: 'https://www.cloudflare.com/privacypolicy/',
                },
            ],
        },
        {
            name: 'Wenku8 (wenku8.com / wenku8.net)',
            scope: 'Android App & Upstream Content Source',
            purpose:
                'Provides novel content, covers, illustrations, avatars, account authentication, cloud bookshelf, and community reviews. Direct image/web requests and relayed account/review actions are processed by Wenku8.',
            links: [{ label: 'wenku8.com', href: 'https://www.wenku8.com/' }],
        },
        {
            name: 'Google AdSense',
            scope: 'Website homepage only (wenku8.app)',
            purpose:
                'Displays web ads on the project homepage to help cover server expenses; may use cookies or device identifiers for ad delivery and measurement (not loaded on /privacy/).',
            links: [
                { label: 'Google Privacy Policy', href: 'https://policies.google.com/privacy' },
            ],
        },
        {
            name: 'Disqus',
            scope: 'Website homepage comments only (wenku8.app)',
            purpose:
                "Powers the homepage comment section under Disqus's own terms (not loaded in the App or on /privacy/).",
            links: [
                {
                    label: 'Disqus Privacy Policy',
                    href: 'https://help.disqus.com/en/articles/1717103-disqus-privacy-policy',
                },
            ],
        },
    ],
};

const TOC_ITEMS = {
    zh: [
        { id: 'controller', label: '1. 联系与控制者' },
        { id: 'local-data', label: '2. 本地数据' },
        { id: 'relay-data', label: '3. 中继与分享服务' },
        { id: 'third-party', label: '4. 第三方服务' },
        { id: 'legal-basis', label: '5. GDPR 法律依据' },
        { id: 'retention', label: '6. 数据保留' },
        { id: 'transfers', label: '7. 跨境传输' },
        { id: 'rights', label: '8. 您的权利' },
        { id: 'children', label: '9. 儿童隐私' },
        { id: 'changes', label: '10. 政策更新' },
    ],
    en: [
        { id: 'controller', label: '1. Data Controller' },
        { id: 'local-data', label: '2. Local Data' },
        { id: 'relay-data', label: '3. Relay & Share' },
        { id: 'third-party', label: '4. Third Parties' },
        { id: 'legal-basis', label: '5. Legal Bases' },
        { id: 'retention', label: '6. Retention' },
        { id: 'transfers', label: '7. Transfers' },
        { id: 'rights', label: '8. Your Rights' },
        { id: 'children', label: '9. Children' },
        { id: 'changes', label: '10. Changes' },
    ],
};

function resolveInitialLanguage() {
    if (typeof window === 'undefined') return 'zh';
    const params = new URLSearchParams(window.location.search);
    const requested = params.get('lang');
    // Strict allow-list validation of query parameter
    if (requested === 'en') return 'en';
    if (requested === 'zh') return 'zh';
    const browserLang = (navigator.language || '').toLowerCase();
    return browserLang.startsWith('zh') ? 'zh' : 'en';
}

const SectionCard = ({ id, title, darkMode, children }) => (
    <section
        id={id}
        className={`scroll-mt-24 rounded-2xl border p-6 md:p-8 transition-colors ${
            darkMode
                ? 'bg-slate-800/80 border-slate-700 text-gray-300'
                : 'bg-white border-gray-200/80 text-gray-700 shadow-sm'
        }`}
    >
        <h2
            className={`text-xl md:text-2xl font-bold mb-4 ${
                darkMode ? 'text-white' : 'text-slate-900'
            }`}
        >
            {title}
        </h2>
        <div className="space-y-4 leading-relaxed text-sm md:text-base">{children}</div>
    </section>
);

const ExternalLinkItem = ({ href, children, darkMode }) => (
    <a
        href={href}
        target="_blank"
        rel="noopener noreferrer"
        className={`inline-flex items-center gap-1 underline underline-offset-2 transition-colors ${
            darkMode
                ? 'text-blue-400 hover:text-blue-300'
                : 'text-blue-600 hover:text-blue-700'
        }`}
    >
        {children}
        <ExternalLink size={13} className="shrink-0" />
    </a>
);

const PrivacyPage = () => {
    const [darkMode, setDarkMode] = useState(
        () =>
            typeof window !== 'undefined' &&
            Boolean(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches),
    );
    const [lang, setLang] = useState(resolveInitialLanguage);

    useEffect(() => {
        document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
        document.title =
            lang === 'zh'
                ? '隐私政策 — 轻小说文库 Android'
                : 'Privacy Policy — Wenku8 Android';
    }, [lang]);

    const handleLanguageChange = (nextLang) => {
        if (nextLang !== 'zh' && nextLang !== 'en') return;
        setLang(nextLang);
        const query = nextLang === 'en' ? '?lang=en' : '?lang=zh';
        const hash = window.location.hash || '';
        window.history.replaceState(null, '', `${window.location.pathname}${query}${hash}`);
    };

    const isZh = lang === 'zh';
    const toc = TOC_ITEMS[lang];
    const thirdParties = THIRD_PARTY_ROWS[lang];

    const codeClass = `px-1.5 py-0.5 rounded text-xs md:text-sm font-mono ${
        darkMode ? 'bg-slate-900 text-blue-300' : 'bg-slate-100 text-slate-800'
    }`;

    const calloutClass = `rounded-xl border p-4 text-sm ${
        darkMode
            ? 'bg-blue-950/40 border-blue-800/60 text-blue-100'
            : 'bg-blue-50/80 border-blue-200 text-blue-950'
    }`;

    return (
        <div
            className={`min-h-screen font-sans transition-colors duration-300 ${
                darkMode ? 'bg-slate-900 text-gray-200' : 'bg-gray-50 text-slate-800'
            }`}
        >
            {/* Top Navigation */}
            <nav
                className={`fixed top-0 inset-x-0 z-50 transition-colors duration-300 backdrop-blur-md shadow-sm ${
                    darkMode ? 'bg-slate-900/90 border-b border-slate-800' : 'bg-white/85 border-b border-gray-200/70'
                }`}
            >
                <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-between h-16 gap-3">
                        <a
                            href="/"
                            className={`inline-flex items-center gap-2 font-semibold text-sm md:text-base transition-colors ${
                                darkMode
                                    ? 'text-blue-400 hover:text-blue-300'
                                    : 'text-blue-600 hover:text-blue-700'
                            }`}
                        >
                            <ArrowLeft size={18} />
                            <span>{isZh ? '返回首页' : 'Back to Home'}</span>
                        </a>

                        <div className="flex items-center gap-2">
                            <div
                                role="group"
                                aria-label="Language switcher"
                                className={`inline-flex items-center rounded-full p-1 border text-xs font-medium ${
                                    darkMode
                                        ? 'bg-slate-800 border-slate-700'
                                        : 'bg-gray-100 border-gray-200'
                                }`}
                            >
                                <button
                                    type="button"
                                    onClick={() => handleLanguageChange('zh')}
                                    className={`px-3 py-1 rounded-full transition-colors ${
                                        isZh
                                            ? 'bg-blue-600 text-white shadow-sm'
                                            : darkMode
                                                ? 'text-gray-300 hover:text-white'
                                                : 'text-gray-600 hover:text-gray-900'
                                    }`}
                                >
                                    简体中文
                                </button>
                                <button
                                    type="button"
                                    onClick={() => handleLanguageChange('en')}
                                    className={`px-3 py-1 rounded-full transition-colors ${
                                        !isZh
                                            ? 'bg-blue-600 text-white shadow-sm'
                                            : darkMode
                                                ? 'text-gray-300 hover:text-white'
                                                : 'text-gray-600 hover:text-gray-900'
                                    }`}
                                >
                                    English
                                </button>
                            </div>

                            <button
                                type="button"
                                onClick={() => setDarkMode(!darkMode)}
                                aria-label={darkMode ? 'Switch to light mode' : 'Switch to dark mode'}
                                className={`p-2 rounded-full transition-colors ${
                                    darkMode
                                        ? 'text-yellow-400 hover:bg-slate-800'
                                        : 'text-slate-600 hover:bg-gray-200'
                                }`}
                            >
                                {darkMode ? <Sun size={19} /> : <Moon size={19} />}
                            </button>
                        </div>
                    </div>
                </div>
            </nav>

            {/* Header */}
            <header className="pt-28 pb-8 px-4 sm:px-6 lg:px-8">
                <div className="max-w-4xl mx-auto text-center">
                    <div
                        className={`inline-flex items-center justify-center p-3 rounded-2xl mb-5 ${
                            darkMode ? 'bg-blue-900/40 text-blue-400' : 'bg-blue-100 text-blue-600'
                        }`}
                    >
                        <ShieldCheck size={32} />
                    </div>
                    <h1
                        className={`text-3xl md:text-5xl font-extrabold tracking-tight mb-3 ${
                            darkMode ? 'text-white' : 'text-slate-900'
                        }`}
                    >
                        {isZh ? '隐私政策' : 'Privacy Policy'}
                    </h1>
                    <p className={`text-sm md:text-base ${darkMode ? 'text-gray-400' : 'text-gray-600'}`}>
                        {isZh
                            ? `轻小说文库 Android (org.mewx.wenku8) · 生效与最后更新日期：${EFFECTIVE_DATE_ZH}`
                            : `Wenku8 Android (org.mewx.wenku8) · Effective & Last Updated: ${EFFECTIVE_DATE_EN}`}
                    </p>
                </div>
            </header>

            {/* Main Content */}
            <main className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 pb-20 space-y-6">
                {/* Intro & Disclaimer */}
                <div
                    className={`rounded-2xl border p-6 md:p-8 space-y-4 ${
                        darkMode
                            ? 'bg-slate-800/80 border-slate-700 text-gray-300'
                            : 'bg-white border-gray-200/80 text-gray-700 shadow-sm'
                    }`}
                >
                    {isZh ? (
                        <>
                            <p className="leading-relaxed text-sm md:text-base">
                                本隐私政策说明独立开发者 <strong className={darkMode ? 'text-white' : 'text-slate-900'}>MewX</strong>（以下简称“我们”）在您使用{' '}
                                <strong className={darkMode ? 'text-white' : 'text-slate-900'}>轻小说文库 Android</strong>（包名：<code className={codeClass}>org.mewx.wenku8</code>，以下简称“本应用”）、其配套的{' '}
                                <strong className={darkMode ? 'text-white' : 'text-slate-900'}>API 中继与书籍分享服务</strong>（含 <code className={codeClass}>share.wenku8.app</code>）以及项目官网{' '}
                                <ExternalLinkItem href={HOMEPAGE_URL} darkMode={darkMode}>https://wenku8.app/</ExternalLinkItem>（以下简称“本网站”）时，如何收集、使用、存储、传输和保护您的信息。
                            </p>
                            <div className={calloutClass}>
                                <strong>重要声明：</strong>本应用是由社区爱好者维护的开源第三方 Android 客户端，与轻小说文库官方网站（<code className={codeClass}>wenku8.com</code> / <code className={codeClass}>wenku8.net</code>）无官方隶属关系。我们不运营轻小说文库账号系统，也不拥有小说正文及社区评论内容的控制权。
                            </div>
                        </>
                    ) : (
                        <>
                            <p className="leading-relaxed text-sm md:text-base">
                                This Privacy Policy explains how independent developer{' '}
                                <strong className={darkMode ? 'text-white' : 'text-slate-900'}>MewX</strong> (&ldquo;we&rdquo;, &ldquo;us&rdquo;, or &ldquo;our&rdquo;) collects, uses, stores, transmits, and protects information when you use the{' '}
                                <strong className={darkMode ? 'text-white' : 'text-slate-900'}>Wenku8 Android</strong> application (package name:{' '}
                                <code className={codeClass}>org.mewx.wenku8</code>, the &ldquo;App&rdquo;), our companion{' '}
                                <strong className={darkMode ? 'text-white' : 'text-slate-900'}>API relay and book-sharing services</strong> (including{' '}
                                <code className={codeClass}>share.wenku8.app</code>), and the project website{' '}
                                <ExternalLinkItem href={HOMEPAGE_URL} darkMode={darkMode}>https://wenku8.app/</ExternalLinkItem> (the &ldquo;Website&rdquo;).
                            </p>
                            <div className={calloutClass}>
                                <strong>Important Notice:</strong> Wenku8 Android is an independent, fan-made, open-source third-party Android client and is <strong>not</strong> officially affiliated with Wenku8 (<code className={codeClass}>wenku8.com</code> / <code className={codeClass}>wenku8.net</code>). We do not operate the Wenku8 account system, nor do we own or control upstream novel content or community reviews.
                            </div>
                        </>
                    )}

                    {/* Quick Jump TOC */}
                    <nav aria-label={isZh ? '目录' : 'Table of contents'} className="pt-2">
                        <div className="flex flex-wrap gap-2">
                            {toc.map((item) => (
                                <a
                                    key={item.id}
                                    href={`#${item.id}`}
                                    className={`text-xs px-3 py-1.5 rounded-full border transition-colors ${
                                        darkMode
                                            ? 'border-slate-700 bg-slate-900/60 text-gray-300 hover:border-blue-500 hover:text-blue-300'
                                            : 'border-gray-200 bg-gray-50 text-gray-600 hover:border-blue-400 hover:text-blue-600'
                                    }`}
                                >
                                    {item.label}
                                </a>
                            ))}
                        </div>
                    </nav>
                </div>

                {/* Section 1 */}
                <SectionCard
                    id="controller"
                    title={isZh ? '1. 数据控制者与联系方式' : '1. Data Controller & Contact Information'}
                    darkMode={darkMode}
                >
                    <ul className="space-y-2 list-none pl-0">
                        <li className="flex flex-wrap items-center gap-2">
                            <strong className={darkMode ? 'text-white' : 'text-slate-900'}>
                                {isZh ? '开发者 / 数据控制者：' : 'Developer / Data Controller:'}
                            </strong>
                            <span>MewX</span>
                        </li>
                        <li className="flex flex-wrap items-center gap-2">
                            <Mail size={16} className={darkMode ? 'text-blue-400' : 'text-blue-600'} />
                            <strong className={darkMode ? 'text-white' : 'text-slate-900'}>
                                {isZh ? '联系邮箱：' : 'Email:'}
                            </strong>
                            <a
                                href={CONTACT_MAILTO}
                                className={`underline underline-offset-2 ${
                                    darkMode
                                        ? 'text-blue-400 hover:text-blue-300'
                                        : 'text-blue-600 hover:text-blue-700'
                                }`}
                            >
                                {CONTACT_EMAIL}
                            </a>
                        </li>
                        <li className="flex flex-wrap items-center gap-2">
                            <Globe size={16} className={darkMode ? 'text-blue-400' : 'text-blue-600'} />
                            <strong className={darkMode ? 'text-white' : 'text-slate-900'}>
                                {isZh ? '官方网站：' : 'Website:'}
                            </strong>
                            <ExternalLinkItem href={HOMEPAGE_URL} darkMode={darkMode}>
                                {HOMEPAGE_URL}
                            </ExternalLinkItem>
                        </li>
                        <li className="flex flex-wrap items-center gap-2">
                            <strong className={darkMode ? 'text-white' : 'text-slate-900'}>
                                {isZh ? '开源仓库：' : 'Source Code:'}
                            </strong>
                            <ExternalLinkItem href={GITHUB_URL} darkMode={darkMode}>
                                GitHub (MewX/light-novel-library_Wenku8_Android)
                            </ExternalLinkItem>
                        </li>
                    </ul>
                </SectionCard>

                {/* Section 2 */}
                <SectionCard
                    id="local-data"
                    title={isZh ? '2. 我们在您设备本地存储的数据' : '2. Data Stored Locally on Your Device'}
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <>
                            <p>
                                本应用优先采用本地存储设计。以下数据仅保存在您的设备本地（应用私有存储、SQLite 数据库与偏好设置中），不会上传至我们自建的用户数据库：
                            </p>
                            <ol className="list-decimal pl-5 space-y-2.5">
                                <li>
                                    <strong>阅读与书架数据：</strong>您的本地书架、缓存或离线下载的小说正文与插图、阅读进度、书签、划线高亮与笔记、阅读时长与字数统计、搜索历史、书评/回复草稿，以及界面语言、主题、字体、翻页方式和网络优化等偏好设置。
                                </li>
                                <li>
                                    <strong>轻小说文库账号凭据（可选）：</strong>本应用支持不登录直接浏览与阅读。如果您选择登录已有的 Wenku8 账号，本应用会在您设备本地以编码形式保存您的用户名/邮箱与密码（<code className={codeClass}>cert.wk8</code>）、缓存头像及会话 Cookie（<code className={codeClass}>PHPSESSID</code>），仅用于自动恢复登录态与同步云端书架。您可随时在应用内退出登录以清除本地保存的凭据与会话。
                                </li>
                                <li>
                                    <strong>备份与迁移：</strong>
                                    <ul className="list-disc pl-5 mt-1.5 space-y-1">
                                        <li>
                                            使用应用内置的本地备份导出功能（<code className={codeClass}>.wk8backup.zip</code>）时，导出文件<strong>严格排除</strong>账号凭据、会话 Cookie、头像缓存和本地网络路由配置。
                                        </li>
                                        <li>
                                            若您的 Android 系统开启了“Android 自动备份”（Auto Backup），系统可能会按 Google 的备份机制加密备份应用数据至您的 Google 账号，您可在 Android 系统设置中关闭该功能。
                                        </li>
                                    </ul>
                                </li>
                            </ol>
                        </>
                    ) : (
                        <>
                            <p>
                                The App is designed to store your personal reading data locally on your device (in app-private storage, Room SQLite database, and DataStore preferences) rather than in any developer-hosted user database:
                            </p>
                            <ol className="list-decimal pl-5 space-y-2.5">
                                <li>
                                    <strong>Reading &amp; Bookshelf Data:</strong> Your local bookshelf, cached or offline-downloaded chapters and illustrations, reading progress, bookmarks, highlights and notes, reading duration and character-count statistics, search history, review/reply drafts, and app settings (language, theme, fonts, reader layout, and network preferences).
                                </li>
                                <li>
                                    <strong>Wenku8 Account Credentials (Optional):</strong> You can browse and read without signing in. If you choose to sign in with an existing Wenku8.com account, the App stores your username/email and password locally in encoded form (<code className={codeClass}>cert.wk8</code>), along with your cached avatar and session cookie (<code className={codeClass}>PHPSESSID</code>), solely to maintain your login session and sync your Wenku8 cloud bookshelf. Signing out clears locally stored credentials and session tokens.
                                </li>
                                <li>
                                    <strong>Backups &amp; Migration:</strong>
                                    <ul className="list-disc pl-5 mt-1.5 space-y-1">
                                        <li>
                                            Manual local backup archives (<code className={codeClass}>.wk8backup.zip</code>) exported from the App <strong>strictly exclude</strong> account credentials, session cookies, cached avatars, and local routing preferences.
                                        </li>
                                        <li>
                                            If Android Auto Backup is enabled on your device, Android may back up app data to your Google account under Google&rsquo;s backup controls; you can disable this in your Android system settings.
                                        </li>
                                    </ul>
                                </li>
                            </ol>
                        </>
                    )}
                </SectionCard>

                {/* Section 3 */}
                <SectionCard
                    id="relay-data"
                    title={
                        isZh
                            ? '3. 通过中继服务器与分享服务处理的数据'
                            : '3. Data Processed by Our Relay & Share Services'
                    }
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <>
                            <p>
                                为保障客户端连接稳定性、兼容性与安全性，本应用的 API 请求会经由我们部署在 <strong>Cloudflare Workers</strong> 上的中继服务转发至第三方上游轻小说文库服务器。
                            </p>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.1 账号与上游 API 转发
                            </h3>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>本应用与中继服务器之间采用加密传输。</li>
                                <li>
                                    当您执行登录、同步云端书架、每日签到或发表书评/回复时，中继服务器仅作为传输通道，将您提交的凭据或 <code className={codeClass}>PHPSESSID</code> 会话 Cookie 实时转发至上游轻小说文库服务器。<strong>我们的中继服务器不会持久化存储您的密码或个人会话 Cookie。</strong>
                                </li>
                                <li>
                                    为协助上游站点进行反滥用/防火墙（WAF）防护，中继服务器会在转发给上游服务器的请求头中附带网络边缘元数据（包括客户端 IP 地址、国家/地区代码、ASN、接入节点、HTTP 协议版本及请求动作类型）。
                                </li>
                                <li>
                                    当书籍元信息命中边缘缓存时，中继服务器可能会在后台异步向上游同步一次点击统计，以维持上游站点的作品点击数据准确。
                                </li>
                            </ul>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.2 服务端运维日志与性能分析
                            </h3>
                            <p>
                                为排查故障、优化不同地区的访问延迟以及防止恶意爬取与滥用，中继服务器会通过 <strong>Cloudflare Analytics Engine</strong> 与低比例采样的 <strong>Cloudflare Workers 日志/链路追踪</strong> 记录以下运维技术数据：
                            </p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>客户端 IP 地址、IP 协议版本（IPv4/IPv6）、国家/地区代码、网络自治系统编号（ASN）与 Cloudflare 接入节点（Colo）；</li>
                                <li>应用版本号、请求路由类型、HTTP 协议、状态码、缓存命中情况及网络往返/响应耗时（RTT / 延迟指标）；</li>
                                <li>经校验的 API 请求动作及参数（<strong>密码字段会被自动剔除，绝不写入分析数据</strong>）。</li>
                            </ul>
                            <p className={`text-xs md:text-sm ${darkMode ? 'text-gray-400' : 'text-gray-500'}`}>
                                注：中继服务器可能对公开的小说元数据、目录及正文内容进行边缘缓存或归档以提升访问速度与可用性，此类缓存/归档不包含任何用户账号、阅读历史或个人数据。
                            </p>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.3 网络诊断与连接优化（可选）
                            </h3>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>
                                    <strong>连接优化（DoH）：</strong>当您启用网络路由优化时，本应用会向公共 DNS-over-HTTPS（DoH）解析服务（如阿里公共 DNS、腾讯 DNSPod）查询域名配置记录，以获取中继可用节点列表。
                                </li>
                                <li>
                                    <strong>网络延迟诊断：</strong>仅在您主动触发诊断或路由测速时发起。该探针为无状态接口，不访问上游站点、不读取或设置 Cookie、不写入数据库或统计表；客户端 IP 仅在内存中用于短期频率限制（限流）。
                                </li>
                                <li>
                                    <strong>诊断报告导出：</strong>您可手动导出网络诊断报告用于反馈问题。导出文件已做隐私脱敏处理，<strong>不包含</strong>您的真实 IP 地址、Wi-Fi SSID/BSSID、设备序列号、Cookie、账号凭据或书架内容，仅包含应用版本、Android API 版本、网络类型/是否开启 VPN 或代理、Cloudflare 节点/国家/ASN 与耗时统计，且仅在您主动分享时才会发送。
                                </li>
                            </ul>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.4 书籍分享预览页（<code className={codeClass}>share.wenku8.app</code>）
                            </h3>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>
                                    分享预览页为公开只读页面，不读取或写入 Cookie，不加载广告或第三方跟踪脚本，并配置了 <code className={codeClass}>Referrer-Policy: no-referrer</code>。
                                </li>
                                <li>仅在缓存未命中时，临时使用访问者 IP 地址进行短期访问频率限制以保护上游服务。</li>
                            </ul>
                        </>
                    ) : (
                        <>
                            <p>
                                To ensure connectivity, protocol compatibility, and security, the App sends encrypted API requests through our relay service hosted on <strong>Cloudflare Workers</strong>, which forwards requests to the third-party upstream Wenku8 servers.
                            </p>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.1 Account &amp; Upstream API Relaying
                            </h3>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Traffic between the App and our relay is encrypted in transit.</li>
                                <li>
                                    When you sign in, sync your cloud bookshelf, check in daily, or post a review/reply, the relay forwards your credentials or <code className={codeClass}>PHPSESSID</code> session cookie directly to the upstream Wenku8 servers. <strong>Our relay does not persistently store user passwords or user session cookies.</strong>
                                </li>
                                <li>
                                    To help the upstream service mitigate abuse and enforce WAF rules, our relay attaches edge network metadata headers to upstream requests (including client IP address, country code, ASN, Cloudflare edge location, HTTP protocol, and API action type).
                                </li>
                                <li>
                                    When book metadata is served from our edge cache, the relay may dispatch a background request to the upstream server to keep upstream novel view counts accurate.
                                </li>
                            </ul>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.2 Operational Telemetry &amp; Logging
                            </h3>
                            <p>
                                To maintain reliability, diagnose latency across regions, and prevent automated abuse, our relay records operational metrics via <strong>Cloudflare Analytics Engine</strong> and low-rate sampled <strong>Cloudflare Workers Observability logs/traces</strong>, including:
                            </p>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>Client IP address, IP family (IPv4/IPv6), country code, Autonomous System Number (ASN), and Cloudflare ingress datacenter (colo);</li>
                                <li>App version, transport route, HTTP protocol, response status, cache status, and network timing/RTT measurements;</li>
                                <li>API action type and validated request parameters (<strong>passwords are stripped and never recorded in analytics</strong>).</li>
                            </ul>
                            <p className={`text-xs md:text-sm ${darkMode ? 'text-gray-400' : 'text-gray-500'}`}>
                                Note: Publicly available novel metadata, tables of contents, and chapter text may be cached or archived on Cloudflare (Cache API, D1, R2) for speed and availability. These caches and archives contain no user accounts, reading histories, or personal data.
                            </p>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.3 Network Diagnostics &amp; Routing Optimization (Optional)
                            </h3>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>
                                    <strong>Routing Optimization (DoH):</strong> When enabled, the App queries public DNS-over-HTTPS (DoH) resolvers (such as AliDNS and DNSPod) for DNS configuration records to obtain candidate relay edge addresses.
                                </li>
                                <li>
                                    <strong>Latency Probe:</strong> Triggered only when you run network diagnostics or route checks. This endpoint is stateless, makes zero upstream calls, ignores cookies, and writes nothing to databases or analytics; client IP is used strictly in-memory for short-term rate limiting.
                                </li>
                                <li>
                                    <strong>Diagnostic Report Export:</strong> Reports you choose to export for troubleshooting are privacy-redacted—they <strong>exclude</strong> raw client/candidate IP addresses, Wi-Fi SSID/BSSID, device serials, cookies, tokens, and bookshelf content—and contain only technical diagnostics (app version, Android API level, network transport type, VPN/proxy status, Cloudflare colo/country/ASN, and latency metrics). Reports are shared only if you manually send them.
                                </li>
                            </ul>

                            <h3 className={`text-base md:text-lg font-semibold pt-2 ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                3.4 Public Book-Share Previews (<code className={codeClass}>share.wenku8.app</code>)
                            </h3>
                            <ul className="list-disc pl-5 space-y-1.5">
                                <li>
                                    Share pages are stateless, public, read-only previews that ignore cookies, load no ads or third-party trackers, and enforce <code className={codeClass}>Referrer-Policy: no-referrer</code>.
                                </li>
                                <li>Client IP is used solely for temporary rate limiting on cache misses to protect upstream infrastructure.</li>
                            </ul>
                        </>
                    )}
                </SectionCard>

                {/* Section 4 */}
                <SectionCard
                    id="third-party"
                    title={isZh ? '4. 第三方服务与 SDK' : '4. Third-Party Services & SDKs'}
                    darkMode={darkMode}
                >
                    <p>
                        {isZh
                            ? '本应用（在配备 Google 移动服务 GMS 的设备上）与本网站集成了以下第三方服务，各服务商按其自身隐私政策独立或作为数据处理者处理相关信息：'
                            : 'The App (on devices with Google Mobile Services / GMS) and the Website integrate the following third-party services, each governed by their own privacy policies:'}
                    </p>

                    <div className="overflow-x-auto -mx-2 px-2">
                        <table
                            className={`w-full text-left border-collapse text-xs md:text-sm ${
                                darkMode ? 'text-gray-300' : 'text-gray-700'
                            }`}
                        >
                            <thead>
                                <tr
                                    className={`border-b ${
                                        darkMode
                                            ? 'border-slate-700 text-white bg-slate-900/50'
                                            : 'border-gray-200 text-slate-900 bg-gray-50'
                                    }`}
                                >
                                    <th className="py-3 px-3 font-semibold whitespace-nowrap">
                                        {isZh ? '服务名称' : 'Service'}
                                    </th>
                                    <th className="py-3 px-3 font-semibold whitespace-nowrap">
                                        {isZh ? '适用范围' : 'Scope'}
                                    </th>
                                    <th className="py-3 px-3 font-semibold min-w-[260px]">
                                        {isZh ? '用途与涉及的数据类型' : 'Purpose & Data Processed'}
                                    </th>
                                    <th className="py-3 px-3 font-semibold whitespace-nowrap">
                                        {isZh ? '隐私政策' : 'Privacy Policy'}
                                    </th>
                                </tr>
                            </thead>
                            <tbody className={`divide-y ${darkMode ? 'divide-slate-700/80' : 'divide-gray-200'}`}>
                                {thirdParties.map((row) => (
                                    <tr key={row.name} className="align-top">
                                        <td className={`py-3 px-3 font-medium ${darkMode ? 'text-white' : 'text-slate-900'}`}>
                                            {row.name}
                                        </td>
                                        <td className="py-3 px-3">{row.scope}</td>
                                        <td className="py-3 px-3 leading-relaxed">{row.purpose}</td>
                                        <td className="py-3 px-3">
                                            <div className="flex flex-col gap-1.5">
                                                {row.links.map((link) => (
                                                    <ExternalLinkItem
                                                        key={link.href}
                                                        href={link.href}
                                                        darkMode={darkMode}
                                                    >
                                                        {link.label}
                                                    </ExternalLinkItem>
                                                ))}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>

                    <div className={calloutClass}>
                        {isZh ? (
                            <>
                                <strong>邮件反馈：</strong>当您主动点击“邮件反馈”向{' '}
                                <a href={CONTACT_MAILTO} className="underline">{CONTACT_EMAIL}</a>{' '}
                                发送邮件时，邮件客户端可能会预填您的应用版本、发行渠道、Android 系统版本与设备型号。只有在您主动发送后，我们才会收到您的邮箱地址及邮件内容，并仅用于回复与排查您反馈的问题。
                            </>
                        ) : (
                            <>
                                <strong>Email Feedback:</strong> If you voluntarily send feedback to{' '}
                                <a href={CONTACT_MAILTO} className="underline">{CONTACT_EMAIL}</a>, your mail client may pre-fill your app version, build channel, Android version, and device model. We receive your email address and message only if you choose to send it, and use it strictly to respond to your inquiry.
                            </>
                        )}
                    </div>
                </SectionCard>

                {/* Section 5 */}
                <SectionCard
                    id="legal-basis"
                    title={
                        isZh
                            ? '5. 处理个人数据的法律依据（适用于 EEA、英国及瑞士用户 / GDPR）'
                            : '5. Legal Bases for Processing (GDPR — EEA, UK & Switzerland)'
                    }
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <>
                            <p>
                                如果您位于欧洲经济区（EEA）、英国或瑞士，我们依据《通用数据保护条例》（GDPR）及同等法律规定的以下法律依据处理您的个人数据：
                            </p>
                            <ol className="list-decimal pl-5 space-y-2">
                                <li>
                                    <strong>履行服务所必需（GDPR 第 6(1)(b) 条）：</strong>响应您在应用内发起的内容浏览、账号会话转发、云端书架同步、书籍分享预览及主动触发的网络诊断请求。
                                </li>
                                <li>
                                    <strong>合法权益（GDPR 第 6(1)(f) 条）：</strong>维护中继服务安全、防止恶意爬虫与滥用（限流/WAF 协同）、监控和降低网络延迟、通过 Crashlytics 修复应用崩溃以提升稳定性。
                                </li>
                                <li>
                                    <strong>您的同意（GDPR 第 6(1)(a) 条）：</strong>在适用法律要求的情况下，用于个性化广告投放（Google AdMob / AdSense）及相关分析。您可随时撤回同意或选择接收非个性化广告。
                                </li>
                            </ol>
                        </>
                    ) : (
                        <>
                            <p>
                                If you are located in the European Economic Area (EEA), the United Kingdom, or Switzerland, we process personal data under the following legal bases:
                            </p>
                            <ol className="list-decimal pl-5 space-y-2">
                                <li>
                                    <strong>Performance of a Contract / Service Request (Art. 6(1)(b) GDPR):</strong> Relaying API requests, maintaining your Wenku8 session and cloud bookshelf sync, serving share previews, and running user-initiated network diagnostics.
                                </li>
                                <li>
                                    <strong>Legitimate Interests (Art. 6(1)(f) GDPR):</strong> Protecting our relay against abuse and scraping, monitoring and optimizing regional latency, and diagnosing crashes via Crashlytics to keep the App reliable.
                                </li>
                                <li>
                                    <strong>Consent (Art. 6(1)(a) GDPR):</strong> Serving personalized advertising (Google AdMob / AdSense) and analytics where required by applicable law. You may withdraw consent or switch to non-personalized ads at any time.
                                </li>
                            </ol>
                        </>
                    )}
                </SectionCard>

                {/* Section 6 */}
                <SectionCard
                    id="retention"
                    title={isZh ? '6. 数据保留期限' : '6. Data Retention'}
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <ul className="list-disc pl-5 space-y-2">
                            <li>
                                <strong>设备本地数据：</strong>保留在您的设备上，直至您手动清除、退出登录或卸载本应用。
                            </li>
                            <li>
                                <strong>中继请求性能与运维统计（Cloudflare Analytics Engine）：</strong>遵循 Cloudflare Analytics Engine 的滚动保留周期（最长约 90 天 / 3 个月），到期自动清除。
                            </li>
                            <li>
                                <strong>中继采样运行日志：</strong>按 Cloudflare Workers 日志服务策略短期保留（通常为数日至数周），仅用于排查异常。
                            </li>
                            <li>
                                <strong>Firebase Crashlytics / Analytics 与 Google AdMob：</strong>遵循 Google / Firebase 的数据保留政策（Crashlytics 崩溃报告默认保留 90 天；Analytics 事件数据通常保留 2 至 14 个月）。
                            </li>
                            <li>
                                <strong>支持与反馈邮件：</strong>在处理完您的咨询或您要求删除后予以清理。
                            </li>
                        </ul>
                    ) : (
                        <ul className="list-disc pl-5 space-y-2">
                            <li>
                                <strong>On-Device Data:</strong> Retained locally until you sign out, clear app data, or uninstall the App.
                            </li>
                            <li>
                                <strong>Relay Operational Metrics (Cloudflare Analytics Engine):</strong> Retained on a rolling basis for up to approximately 90 days (3 months) per Cloudflare&rsquo;s default retention policy, then automatically purged.
                            </li>
                            <li>
                                <strong>Sampled Worker Logs:</strong> Retained short-term (typically days to weeks) per Cloudflare Workers Observability defaults.
                            </li>
                            <li>
                                <strong>Firebase Crashlytics / Analytics &amp; Google AdMob:</strong> Retained according to Google/Firebase retention policies (e.g., 90 days for Crashlytics reports; 2–14 months for Analytics event data).
                            </li>
                            <li>
                                <strong>Support Emails:</strong> Retained only as long as necessary to resolve your inquiry or until you request deletion.
                            </li>
                        </ul>
                    )}
                </SectionCard>

                {/* Section 7 */}
                <SectionCard
                    id="transfers"
                    title={isZh ? '7. 跨境数据传输' : '7. International Data Transfers'}
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <p>
                            由于本服务使用全球分布式基础设施（Cloudflare 边缘节点、Google / Firebase 服务器）并连接上游 Wenku8 服务器，您的请求与技术数据可能会被传输至您所在国家/地区以外的服务器进行处理（包括美国及其他设有数据中心的地区）。Cloudflare 与 Google 均采用符合适用数据保护法规的跨境传输保障机制（如欧盟标准合同条款 SCCs 及欧盟-美国数据隐私框架等）。
                        </p>
                    ) : (
                        <p>
                            Because we rely on globally distributed infrastructure (Cloudflare&rsquo;s edge network, Google/Firebase servers) and connect to Wenku8&rsquo;s upstream servers, your requests and technical data may be processed outside your country of residence, including in the United States. Where applicable, Google and Cloudflare rely on recognized cross-border transfer mechanisms (such as Standard Contractual Clauses and the EU-U.S. Data Privacy Framework).
                        </p>
                    )}
                </SectionCard>

                {/* Section 8 */}
                <SectionCard
                    id="rights"
                    title={isZh ? '8. 您的隐私权利与自主控制方式' : '8. Your Privacy Rights & Controls'}
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <>
                            <p>
                                无论您身处何地（包括享有 GDPR 权利的 EEA、英国及瑞士用户），您均可通过以下方式管理您的数据或行使访问、更正、删除、限制处理、数据可携带、反对处理及撤回同意等权利：
                            </p>
                            <ol className="list-decimal pl-5 space-y-2">
                                <li>
                                    <strong>控制广告个性化与广告 ID：</strong>在 Android 系统的 <em>设置 → Google → 广告</em> 中，您可以重置或删除设备广告 ID，或选择停用广告个性化；在 EEA/英国地区，您也可通过广告同意弹窗随时调整偏好。
                                </li>
                                <li>
                                    <strong>清除本地数据：</strong>您可以在应用设置中退出登录、清除缓存，或在 Android 系统应用信息中清除全部应用数据/卸载本应用；也可使用应用内置的本地备份导出您自己的阅读数据。
                                </li>
                                <li>
                                    <strong>管理 Wenku8 账号数据：</strong>轻小说文库账号及公开发表的书评由 <code className={codeClass}>wenku8.com</code> 托管，如需修改或注销账号，请直接联系 <code className={codeClass}>wenku8.com</code> 站点管理方。
                                </li>
                                <li>
                                    <strong>向我们提交隐私请求：</strong>请发送邮件至{' '}
                                    <a href={CONTACT_MAILTO} className="underline text-blue-500">{CONTACT_EMAIL}</a>。请注意，由于我们不维护独立的个人用户账号库，对于中继运维日志等纯技术数据，我们可能需要您提供必要的技术信息（如大致访问时间与 IP）以核实，或因无法关联到特定自然人而无法单独定位。
                                </li>
                                <li>
                                    <strong>向监管机构申诉：</strong>若您位于 EEA、英国或瑞士，您有权向当地的数据保护监管机构提出申诉。
                                </li>
                            </ol>
                        </>
                    ) : (
                        <>
                            <p>
                                Depending on your jurisdiction (including EEA, UK, and Switzerland under GDPR), you have the right to request access to, correction of, deletion of, restriction of, or portability of your personal data, to object to processing based on legitimate interests, and to withdraw consent at any time.
                            </p>
                            <ul className="list-disc pl-5 space-y-2">
                                <li>
                                    <strong>Advertising Controls:</strong> You can reset or delete your Android Advertising ID or opt out of personalized ads via <em>Android Settings → Google → Ads</em> (and via consent dialogs where applicable).
                                </li>
                                <li>
                                    <strong>Delete Local Data:</strong> Sign out inside the App, clear App storage in Android Settings, or uninstall the App. You may export your reading data anytime using the built-in local backup tool.
                                </li>
                                <li>
                                    <strong>Wenku8 Account Data:</strong> Accounts and community reviews are hosted by <code className={codeClass}>wenku8.com</code>. Please contact <code className={codeClass}>wenku8.com</code> directly to modify or delete your Wenku8 account.
                                </li>
                                <li>
                                    <strong>Submit a Privacy Request:</strong> Contact us at{' '}
                                    <a href={CONTACT_MAILTO} className="underline text-blue-500">{CONTACT_EMAIL}</a>. Because we do not maintain our own user account database, requests regarding technical relay logs may require identifying details (such as approximate timestamp and IP address) or may not be linkable to a specific individual.
                                </li>
                                <li>
                                    <strong>Supervisory Authority:</strong> EEA, UK, and Swiss residents also have the right to lodge a complaint with their local data protection authority.
                                </li>
                            </ul>
                        </>
                    )}
                </SectionCard>

                {/* Section 9 */}
                <SectionCard
                    id="children"
                    title={isZh ? '9. 儿童隐私保护' : "9. Children's Privacy"}
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <p>
                            本应用与本网站并非面向 13 周岁以下（在部分欧洲经济区国家为 16 周岁以下）的儿童设计，我们也不会明知而主动收集儿童的个人数据。如您是家长或监护人，发现您的孩子在未经同意的情况下向我们提供了个人数据，请通过{' '}
                            <a href={CONTACT_MAILTO} className="underline text-blue-500">{CONTACT_EMAIL}</a>{' '}
                            联系我们，我们将尽快核实并删除。
                        </p>
                    ) : (
                        <p>
                            The App and Website are not directed to children under 13 (or under 16 in certain EEA jurisdictions), and we do not knowingly collect personal data from children. If you believe a child has provided personal data without parental consent, please contact{' '}
                            <a href={CONTACT_MAILTO} className="underline text-blue-500">{CONTACT_EMAIL}</a>{' '}
                            so we can delete it promptly.
                        </p>
                    )}
                </SectionCard>

                {/* Section 10 */}
                <SectionCard
                    id="changes"
                    title={isZh ? '10. 本隐私政策的更新' : '10. Changes to This Policy'}
                    darkMode={darkMode}
                >
                    {isZh ? (
                        <p>
                            我们可能会因功能变更、第三方 SDK 调整或法律法规要求而适时更新本隐私政策。更新后的版本将发布在{' '}
                            <a href={PRIVACY_URL} className="underline text-blue-500">{PRIVACY_URL}</a>{' '}
                            并更新页面顶部的“最后更新日期”。如涉及重大变更，我们也会通过应用内公告或更新说明提示您。
                        </p>
                    ) : (
                        <p>
                            We may update this Privacy Policy from time to time to reflect changes in features, third-party services, or legal requirements. Updates will be published at{' '}
                            <a href={PRIVACY_URL} className="underline text-blue-500">{PRIVACY_URL}</a>{' '}
                            with a revised &ldquo;Last Updated&rdquo; date.
                        </p>
                    )}
                </SectionCard>
            </main>

            {/* Footer */}
            <footer
                className={`py-10 border-t ${
                    darkMode
                        ? 'bg-slate-950 border-slate-800 text-gray-400'
                        : 'bg-gray-900 border-gray-800 text-gray-400'
                }`}
            >
                <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 text-center text-sm space-y-2">
                    <p className="font-semibold text-gray-200">Wenku8 Android</p>
                    <p>
                        &copy; 2014-2027 MewX &amp; Contributors.{' '}
                        {isZh ? '保留所有权利。' : 'All rights reserved.'}
                    </p>
                    <p className="text-xs text-gray-500">
                        <a href="/" className="hover:text-gray-300 underline underline-offset-2">
                            {isZh ? '返回首页' : 'Homepage'}
                        </a>
                        {' · '}
                        <a href={CONTACT_MAILTO} className="hover:text-gray-300 underline underline-offset-2">
                            {CONTACT_EMAIL}
                        </a>
                    </p>
                </div>
            </footer>
        </div>
    );
};

export default PrivacyPage;

import React, { useState, useEffect, useCallback } from 'react';
import {
    Download,
    Github,
    Coffee,
    Menu,
    X,
    Moon,
    Sun,
    User,
    Calendar,
    ExternalLink,
    Heart,
    ChevronRight,
    ChevronLeft,
    Globe,
    History,
    ArrowRight,
    MessageSquare,
    Archive,
    Send,
    Sparkles,
    Layers,
    BookOpen,
    ShieldCheck,
    Tablet,
    Check,
    Maximize2,
    HardDriveDownload,
    Wifi,
} from 'lucide-react';
import {
    SCREENSHOTS,
    STAFF,
    COMMUNITY_CREDITS,
    BLOG_POSTS,
} from './data/siteData';

// --- Decorative Material 3 Expressive Organic Shape ---

const MaterialSunnyBadge = ({ className = '' }) => (
    <svg
        viewBox="0 0 120 120"
        fill="currentColor"
        aria-hidden="true"
        className={className}
    >
        <path d="M60 4C66.8 4 71.5 11.4 77.7 14C84 16.6 92.4 13.7 97.2 18.5C102 23.3 99.1 31.7 101.7 38C104.3 44.2 111.7 48.9 111.7 55.7C111.7 62.5 104.3 67.2 101.7 73.4C99.1 79.7 102 88.1 97.2 92.9C92.4 97.7 84 94.8 77.7 97.4C71.5 100 66.8 107.4 60 107.4C53.2 107.4 48.5 100 42.3 97.4C36 94.8 27.6 97.7 22.8 92.9C18 88.1 20.9 79.7 18.3 73.4C15.7 67.2 8.3 62.5 8.3 55.7C8.3 48.9 15.7 44.2 18.3 38C20.9 31.7 18 23.3 22.8 18.5C27.6 13.7 36 16.6 42.3 14C48.5 11.4 53.2 4 60 4Z" />
    </svg>
);

// --- Safe Post Content Renderer (No raw HTML injection) ---

const PostParagraphs = ({ paragraphs }) => (
    <div className="space-y-2.5 text-sm leading-relaxed text-[var(--md-on-surface-variant)]">
        {paragraphs.map((segments, pIdx) => (
            <p key={pIdx} className="whitespace-pre-line break-words">
                {segments.map((seg, sIdx) => {
                    if (seg.href) {
                        return (
                            <a
                                key={sIdx}
                                href={seg.href}
                                target="_blank"
                                rel="noopener noreferrer"
                                className={`underline underline-offset-2 font-medium transition-colors ${
                                    seg.highlight
                                        ? 'text-rose-500 hover:text-rose-400'
                                        : 'text-[var(--md-primary)] hover:opacity-80'
                                }`}
                            >
                                {seg.text}
                            </a>
                        );
                    }
                    if (seg.highlight) {
                        return (
                            <span key={sIdx} className="text-rose-500 font-semibold">
                                {seg.text}
                            </span>
                        );
                    }
                    return <React.Fragment key={sIdx}>{seg.text}</React.Fragment>;
                })}
            </p>
        ))}
    </div>
);

// --- Navbar (Floating M3 Expressive Top Bar) ---

const NAV_LINKS = [
    { href: '#home', label: '首页' },
    { href: '#features', label: '2.0 特性' },
    { href: '#news', label: '开发日志' },
    { href: '#staff', label: '制作团队' },
    { href: '#comments', label: '留言板' },
    { href: '/privacy/', label: '隐私政策' },
];

const Navbar = ({ darkMode, toggleDarkMode }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [scrolled, setScrolled] = useState(false);

    useEffect(() => {
        const onScroll = () => setScrolled(window.scrollY > 18);
        onScroll();
        window.addEventListener('scroll', onScroll, { passive: true });
        return () => window.removeEventListener('scroll', onScroll);
    }, []);

    return (
        <header className="fixed top-0 inset-x-0 z-50 px-3 sm:px-6 pt-3 pointer-events-none">
            <nav
                aria-label="主导航"
                className={`pointer-events-auto max-w-7xl mx-auto rounded-full border m3-emphasized backdrop-blur-xl ${
                    scrolled
                        ? 'bg-[var(--md-surface-container)]/90 border-[var(--md-outline-variant)] shadow-lg shadow-black/5'
                        : 'bg-[var(--md-surface)]/80 border-[var(--md-outline-variant)]/70 shadow-sm'
                }`}
            >
                <div className="px-4 sm:px-6 h-15 flex items-center justify-between gap-3">
                    <a href="#home" className="flex items-center gap-2.5 shrink-0 group">
                        <div className="w-8 h-8 rounded-full bg-[var(--md-primary-container)] p-1.5 flex items-center justify-center m3-spring group-hover:scale-110">
                            <img
                                src="imgs/app/app_icon.png"
                                alt="轻小说文库图标"
                                className="w-full h-full object-contain"
                            />
                        </div>
                        <span className="font-bold text-base sm:text-lg tracking-tight text-[var(--md-on-surface)]">
                            轻小说文库{' '}
                            <span className="text-[var(--md-primary)]">Android 2.0</span>
                        </span>
                    </a>

                    <div className="hidden lg:flex items-center gap-1">
                        {NAV_LINKS.map((item) => (
                            <a
                                key={item.href}
                                href={item.href}
                                className="px-3.5 py-2 rounded-full text-sm font-medium text-[var(--md-on-surface-variant)] hover:bg-[var(--md-primary-container)] hover:text-[var(--md-on-primary-container)] m3-emphasized"
                            >
                                {item.label}
                            </a>
                        ))}

                        <button
                            type="button"
                            onClick={toggleDarkMode}
                            aria-label={darkMode ? '切换为浅色主题' : '切换为深色主题'}
                            className="ml-2 p-2.5 rounded-full bg-[var(--md-surface-container-high)] text-[var(--md-on-surface)] hover:bg-[var(--md-primary-container)] hover:text-[var(--md-on-primary-container)] m3-spring hover:rotate-12 cursor-pointer"
                        >
                            {darkMode ? <Sun size={18} /> : <Moon size={18} />}
                        </button>
                    </div>

                    <div className="flex lg:hidden items-center gap-1.5">
                        <button
                            type="button"
                            onClick={toggleDarkMode}
                            aria-label={darkMode ? '切换为浅色主题' : '切换为深色主题'}
                            className="p-2.5 rounded-full bg-[var(--md-surface-container-high)] text-[var(--md-on-surface)] cursor-pointer"
                        >
                            {darkMode ? <Sun size={18} /> : <Moon size={18} />}
                        </button>
                        <button
                            type="button"
                            onClick={() => setIsOpen((prev) => !prev)}
                            aria-label={isOpen ? '关闭菜单' : '打开菜单'}
                            aria-expanded={isOpen}
                            className="p-2.5 rounded-full bg-[var(--md-surface-container-high)] text-[var(--md-on-surface)] cursor-pointer"
                        >
                            {isOpen ? <X size={20} /> : <Menu size={20} />}
                        </button>
                    </div>
                </div>
            </nav>

            {/* Mobile Expressive Sheet Menu */}
            {isOpen && (
                <div className="pointer-events-auto lg:hidden max-w-7xl mx-auto mt-2 rounded-[28px] border border-[var(--md-outline-variant)] bg-[var(--md-surface-container)]/95 backdrop-blur-xl shadow-xl p-3">
                    <div className="grid grid-cols-2 gap-1.5">
                        {NAV_LINKS.map((item) => (
                            <a
                                key={item.href}
                                href={item.href}
                                onClick={() => setIsOpen(false)}
                                className="px-4 py-3 rounded-2xl text-sm font-medium text-[var(--md-on-surface)] hover:bg-[var(--md-primary-container)] hover:text-[var(--md-on-primary-container)] m3-emphasized"
                            >
                                {item.label}
                            </a>
                        ))}
                    </div>
                </div>
            )}
        </header>
    );
};

// --- Hero Section (M3 Expressive Split Layout + Equal-Width Button Grids) ---

const Hero = ({ onSelectScreenshot }) => {
    const heroShots = [
        SCREENSHOTS.find((s) => s.id === 'bookshelf-grid'),
        SCREENSHOTS.find((s) => s.id === 'bookshelf-spine-scroll'),
        SCREENSHOTS.find((s) => s.id === 'novel-detail'),
    ].filter(Boolean);

    return (
        <section
            id="home"
            className="relative pt-28 pb-16 md:pt-36 md:pb-24 overflow-hidden"
        >
            {/* Ambient Hero Backdrop */}
            <div className="absolute inset-0 z-0 pointer-events-none">
                <img
                    src="imgs/site/tab_bg.jpg"
                    alt=""
                    aria-hidden="true"
                    className="w-full h-full object-cover object-top opacity-20 dark:opacity-15"
                />
                <div
                    className="absolute inset-0"
                    style={{
                        background:
                            'linear-gradient(180deg, color-mix(in srgb, var(--md-background) 42%, transparent) 0%, color-mix(in srgb, var(--md-background) 88%, transparent) 68%, var(--md-background) 100%)',
                    }}
                />
                {/* Soft M3 Tonal Ambient Orbs */}
                <div
                    className="absolute -top-24 -left-24 w-96 h-96 rounded-full blur-3xl opacity-35"
                    style={{ background: 'var(--md-primary-container)' }}
                />
                <div
                    className="absolute top-1/4 -right-24 w-96 h-96 rounded-full blur-3xl opacity-30"
                    style={{ background: 'var(--md-tertiary-container)' }}
                />
            </div>

            <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-12 lg:gap-10 items-center">
                    {/* Left Column: Expressive Typography & Aligned Action Grids */}
                    <div className="lg:col-span-7 text-center lg:text-left">
                        <div className="inline-flex items-center gap-3 mb-6">
                            <div className="relative w-14 h-14 flex items-center justify-center animate-float-gentle">
                                <MaterialSunnyBadge className="absolute inset-0 w-full h-full text-[var(--md-primary-container)] animate-spin-very-slow" />
                                <img
                                    src="imgs/app/app_icon.png"
                                    alt="轻小说文库 App 图标"
                                    className="relative w-8 h-8 object-contain"
                                />
                            </div>
                            <span className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-full text-xs sm:text-sm font-semibold bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)]">
                                <Sparkles size={14} />
                                100% Kotlin + Jetpack Compose · Material 3 Expressive
                            </span>
                        </div>

                        <h1 className="text-4xl sm:text-5xl lg:text-6xl font-extrabold tracking-tight leading-[1.12] text-[var(--md-on-surface)]">
                            轻小说文库{' '}
                            <span className="text-[var(--md-primary)] inline-flex items-center gap-2.5 flex-wrap justify-center lg:justify-start">
                                <span>Android</span>
                                <span className="inline-flex items-center px-3.5 py-0.5 rounded-2xl text-3xl sm:text-4xl lg:text-5xl bg-[var(--md-primary)] text-[var(--md-on-primary)] shadow-md shadow-blue-500/20">
                                    2.0
                                </span>
                            </span>
                        </h1>

                        <p className="mt-6 max-w-2xl mx-auto lg:mx-0 text-base sm:text-lg leading-relaxed text-[var(--md-on-surface-variant)]">
                            初代 Material Design 1 发布十余年后的彻底世代重构。全新打造{' '}
                            <strong className="font-semibold text-[var(--md-on-surface)]">
                                3D 立体书脊
                            </strong>
                            、
                            <strong className="font-semibold text-[var(--md-on-surface)]">
                                六模式中文排版引擎
                            </strong>
                            、
                            <strong className="font-semibold text-[var(--md-on-surface)]">
                                Artbook 插图灯箱
                            </strong>
                            与
                            <strong className="font-semibold text-[var(--md-on-surface)]">
                                墨水屏高对比模式
                            </strong>
                            ；支持从 <code className="font-mono text-sm px-1.5 py-0.5 rounded-md bg-[var(--md-surface-container-high)]">1.x</code> 原位无损覆盖升级，开源、简洁、完全免费。
                        </p>

                        {/* Primary CTAs — Equal-Width 3-Column Grid */}
                        <div className="mt-9 grid grid-cols-1 sm:grid-cols-3 gap-3 max-w-[620px] mx-auto lg:mx-0">
                            <a
                                href="https://play.google.com/store/apps/details?id=org.mewx.wenku8"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-2 px-4 py-3.5 rounded-full whitespace-nowrap bg-[var(--md-primary)] text-[var(--md-on-primary)] font-semibold text-sm sm:text-[15px] shadow-lg shadow-blue-600/25 m3-spring hover:scale-[1.03] hover:rounded-[20px]"
                            >
                                <Download size={18} className="shrink-0" />
                                从 Play Store 安装
                            </a>
                            <a
                                href="https://buymeacoffee.com/mewx"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-2 px-4 py-3.5 rounded-full whitespace-nowrap bg-[var(--md-warm-container)] text-[var(--md-on-warm-container)] font-semibold text-sm sm:text-[15px] border border-amber-500/20 m3-spring hover:scale-[1.03] hover:rounded-[20px]"
                            >
                                <Coffee size={18} className="shrink-0" />
                                赞助服务器
                            </a>
                            <a
                                href="https://github.com/MewX/light-novel-library_Wenku8_Android"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-2 px-4 py-3.5 rounded-full whitespace-nowrap bg-[var(--md-surface-container-high)] text-[var(--md-on-surface)] border border-[var(--md-outline-variant)] font-semibold text-sm sm:text-[15px] m3-spring hover:scale-[1.03] hover:bg-[var(--md-primary-container)] hover:text-[var(--md-on-primary-container)]"
                            >
                                <Github size={18} className="shrink-0" />
                                查看源码
                            </a>
                        </div>

                        {/* Secondary Quick Links — Equal-Width 4-Column Grid */}
                        <div className="mt-4 grid grid-cols-2 sm:grid-cols-4 gap-2.5 max-w-[620px] mx-auto lg:mx-0 text-xs sm:text-sm">
                            <a
                                href="https://t.me/wk8app"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-full whitespace-nowrap bg-[#2AABEE]/15 text-[#198BC8] dark:text-[#66C7F7] border border-[#2AABEE]/30 font-medium m3-emphasized hover:bg-[#2AABEE] hover:text-white"
                            >
                                <Send size={14} className="shrink-0" /> Telegram 群组
                            </a>
                            <a
                                href="https://www.wenku8.com"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-full whitespace-nowrap bg-[var(--md-surface-container)] text-[var(--md-on-surface-variant)] border border-[var(--md-outline-variant)] font-medium m3-emphasized hover:bg-[var(--md-surface-container-high)] hover:text-[var(--md-on-surface)]"
                            >
                                <Globe size={14} className="shrink-0" /> wenku8.com
                            </a>
                            <a
                                href="https://mewx.org"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-full whitespace-nowrap bg-[var(--md-surface-container)] text-[var(--md-on-surface-variant)] border border-[var(--md-outline-variant)] font-medium m3-emphasized hover:bg-[var(--md-surface-container-high)] hover:text-[var(--md-on-surface)]"
                            >
                                <User size={14} className="shrink-0" /> mewx.org
                            </a>
                            <a
                                href="https://web.archive.org/web/*/wenku8.mewx.org"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="w-full inline-flex items-center justify-center gap-1.5 px-3 py-2.5 rounded-full whitespace-nowrap bg-[var(--md-surface-container)] text-[var(--md-on-surface-variant)] border border-[var(--md-outline-variant)] font-medium m3-emphasized hover:bg-[var(--md-surface-container-high)] hover:text-[var(--md-on-surface)]"
                            >
                                <Archive size={14} className="shrink-0" /> 旧版网页
                            </a>
                        </div>

                        <div className="mt-6 flex flex-wrap items-center justify-center lg:justify-start gap-3 text-xs sm:text-sm text-[var(--md-on-surface-variant)]">
                            <a
                                href="https://github.com/MewX/light-novel-library_Wenku8_Android/releases"
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full bg-emerald-500/12 text-emerald-700 dark:text-emerald-300 border border-emerald-500/25 font-mono font-semibold hover:bg-emerald-500/20 m3-emphasized"
                            >
                                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                                v2.0 正式发布 · GitHub Releases
                            </a>
                            <span className="opacity-75">
                                Android 6.0+ · 原位升级保留全部 1.x 数据
                            </span>
                        </div>
                    </div>

                    {/* Right Column: Overlapping 3-Phone M3 Expressive Composition */}
                    <div className="lg:col-span-5 flex justify-center items-center py-4 lg:py-0">
                        <div className="relative w-full max-w-[440px] h-[420px] sm:h-[470px] flex items-center justify-center">
                            <div
                                className="absolute w-72 h-72 sm:w-80 sm:h-80 rounded-[44px] rotate-6 opacity-65"
                                style={{
                                    background:
                                        'linear-gradient(135deg, var(--md-primary-container) 0%, var(--md-tertiary-container) 100%)',
                                }}
                            />

                            {heroShots[0] && (
                                <button
                                    type="button"
                                    onClick={() => onSelectScreenshot(heroShots[0])}
                                    aria-label={`查看大图：${heroShots[0].title}`}
                                    className="group absolute left-0 sm:left-2 top-12 w-36 sm:w-44 rounded-[26px] p-1.5 bg-[var(--md-surface)] border border-[var(--md-outline-variant)] shadow-xl -rotate-6 m3-spring hover:-translate-y-3 hover:rotate-[-3deg] hover:z-30 cursor-pointer"
                                >
                                    <img
                                        src={heroShots[0].src}
                                        alt={heroShots[0].title}
                                        className="w-full h-auto rounded-[20px] block"
                                    />
                                </button>
                            )}

                            {heroShots[2] && (
                                <button
                                    type="button"
                                    onClick={() => onSelectScreenshot(heroShots[2])}
                                    aria-label={`查看大图：${heroShots[2].title}`}
                                    className="group absolute right-0 sm:right-2 top-14 w-36 sm:w-44 rounded-[26px] p-1.5 bg-[var(--md-surface)] border border-[var(--md-outline-variant)] shadow-xl rotate-6 m3-spring hover:-translate-y-3 hover:rotate-[3deg] hover:z-30 cursor-pointer"
                                >
                                    <img
                                        src={heroShots[2].src}
                                        alt={heroShots[2].title}
                                        className="w-full h-auto rounded-[20px] block"
                                    />
                                </button>
                            )}

                            {heroShots[1] && (
                                <button
                                    type="button"
                                    onClick={() => onSelectScreenshot(heroShots[1])}
                                    aria-label={`查看大图：${heroShots[1].title}`}
                                    className="group relative z-20 w-44 sm:w-52 rounded-[30px] p-2 bg-[var(--md-surface)] border-2 border-[var(--md-primary)]/40 shadow-2xl shadow-blue-900/20 m3-spring hover:-translate-y-2.5 hover:scale-[1.03] cursor-pointer"
                                >
                                    <img
                                        src={heroShots[1].src}
                                        alt={heroShots[1].title}
                                        className="w-full h-auto rounded-[22px] block"
                                    />
                                    <span className="absolute -bottom-3.5 left-1/2 -translate-x-1/2 whitespace-nowrap px-3.5 py-1 rounded-full text-xs font-semibold bg-[var(--md-primary)] text-[var(--md-on-primary)] shadow-md">
                                        3D 立体书脊 · 点击放大
                                    </span>
                                </button>
                            )}
                        </div>
                    </div>
                </div>
            </div>
        </section>
    );
};

// --- Unified 2.0 Features + Pixel 8 Screenshot Showcases + Lightbox ---

const PhonePreviewTile = ({ shot, label, onSelect }) => (
    <button
        type="button"
        onClick={() => onSelect(shot)}
        aria-label={`全屏查看：${shot.title}`}
        className="group flex flex-col rounded-[22px] p-2 bg-[var(--md-surface-container)] border border-[var(--md-outline-variant)]/80 shadow-sm text-left m3-spring hover:-translate-y-1.5 hover:shadow-xl hover:border-[var(--md-primary)]/50 cursor-pointer"
    >
        <div className="relative w-full overflow-hidden rounded-[16px] bg-[var(--md-surface)] border border-[var(--md-outline-variant)]/50">
            <img
                src={shot.src}
                alt={shot.title}
                loading="lazy"
                className="w-full h-auto block m3-emphasized group-hover:scale-[1.02]"
            />
            <span className="absolute top-2 right-2 w-7 h-7 rounded-full bg-black/55 text-white backdrop-blur-md flex items-center justify-center opacity-0 group-hover:opacity-100 m3-emphasized">
                <Maximize2 size={13} />
            </span>
        </div>
        <span className="mt-2 px-1 pb-0.5 text-xs font-semibold text-[var(--md-on-surface)] group-hover:text-[var(--md-primary)] m3-emphasized truncate">
            {label}
        </span>
    </button>
);

const SHOWCASE_GROUPS = [
    {
        id: 'bookshelf-showcase',
        badge: 'BOOKSHELF · ROOM WAL',
        icon: Layers,
        accent: 'bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)]',
        title: '3D 立体书脊 & 本地优先三视图书架',
        lead: '采用 Room SQLite (WAL) 管理书架元数据、阅读进度与离线缓存，打造极具藏书质感的本地阅读空间：',
        bullets: [
            {
                lead: '三视图自由切换',
                text: '支持网格、列表与全新 3D 立体书脊视图；真实透视书脊与封面展开、滑动惯性吸附，并将近期阅读作品自动前置队首。',
            },
            {
                lead: '继续阅读 & 智能云端同步',
                text: '内置“继续阅读”轮播卡片与进入书架自动云端同步（防抖 TTL + 互斥保护），并将更新操作收纳于折叠式「云端同步」按钮。',
            },
            {
                lead: '四策略离线缓存',
                text: '支持智能更新、仅下载未缓存章节、分卷自由勾选与全本覆盖重下，随时随地畅快离线阅读。',
            },
        ],
        shotGridClass: 'grid-cols-3',
        reverseDesktop: false,
        shots: [
            { id: 'bookshelf-grid', label: '本地书架 · 网格' },
            { id: 'bookshelf-spine-home', label: '3D 书脊 · 继续阅读' },
            { id: 'bookshelf-spine-scroll', label: '3D 书脊 · 滑动浏览' },
        ],
    },
    {
        id: 'discover-showcase',
        badge: 'MATERIAL 3 EXPRESSIVE',
        icon: Sparkles,
        accent: 'bg-[var(--md-secondary-container)] text-[var(--md-on-secondary-container)]',
        title: 'Material 3 Expressive 自适应架构与全站发现',
        lead: '完全抛弃旧版繁杂的视图栈，以 100% Kotlin + Jetpack Compose 带来更轻盈直觉的浏览体验：',
        bullets: [
            {
                lead: '四栏自适应主导航',
                text: '精简为「书架 · 最新 · 排行 · 我的」四栏导航，自动适配手机底部导航栏与平板 / 折叠屏 NavigationRail。',
            },
            {
                lead: '二维排行榜与即时检索',
                text: '覆盖点击 / 推荐 / 收藏 / 字数 × 日 / 周 / 月 / 总榜，支持连接式分段按钮切换与本地/全站混合搜索。',
            },
            {
                lead: '沉浸式详情与动效',
                text: '作品封面共享元素过渡、Android 12+ 动态壁纸取色、分卷折叠目录、拆分式阅读主按钮与完整社区书评。',
            },
        ],
        shotGridClass: 'grid-cols-2',
        reverseDesktop: true,
        shots: [
            { id: 'rankings', label: '二维排行榜矩阵' },
            { id: 'novel-detail', label: '作品详情与分卷目录' },
        ],
    },
    {
        id: 'reader-showcase',
        badge: 'CJK READER & ARTBOOK',
        icon: BookOpen,
        accent: 'bg-[var(--md-tertiary-container)] text-[var(--md-on-tertiary-container)]',
        title: '六模式中文排版阅读引擎 & Artbook 插图灯箱',
        lead: '基于原生 StaticLayout 深度定制的 CJK 排版分页引擎，兼顾精致阅读质感与海量插图体验：',
        bullets: [
            {
                lead: '六种翻页与排版模式',
                text: '内置覆盖翻页、仿真卷曲、平滑滑动、上下卷轴、日式竖排（从右至左）与墨水屏极速，满足各类阅读习惯。',
            },
            {
                lead: '半透明实时排版工作室',
                text: '即时预览字号、行距、段距、页边距、护眼纸张、繁简转换与自定义字体，内置全文检索、章节书签与系统 TTS 朗读。',
            },
            {
                lead: 'Artbook 插图画集灯箱',
                text: '一键抽离本卷全部插图，支持双指无级缩放、跨页彩插左右拼合、底部胶片缩略图速览与高清原图保存。',
            },
        ],
        shotGridClass: 'grid-cols-3',
        reverseDesktop: false,
        shots: [
            { id: 'reader-hud', label: '阅读引擎与悬浮栏' },
            { id: 'reader-typography', label: '排版与纸张工作室' },
            { id: 'artbook', label: 'Artbook 插图灯箱' },
        ],
    },
];

const INFRA_HIGHLIGHTS = [
    {
        icon: ShieldCheck,
        title: '1.x 原位无损自动迁移',
        description:
            '严格保留正式版包名 org.mewx.wenku8，从旧版覆盖升级后自动无损迁移本地书架、阅读进度、离线小说文本与插图及登录凭据。',
    },
    {
        icon: HardDriveDownload,
        title: '.wk8backup.zip 便携备份',
        description:
            '全新跨设备一键导出与智能合并导入方案，完整保护书架与阅读记录，且导出归档严格排除账号密码与会话 Cookie。',
    },
    {
        icon: Wifi,
        title: '自动中转优化与深度链接',
        description:
            '默认开启 Cloudflare 自动中转连通性评估与网络诊断中心；支持 share.wenku8.app 书籍直达链接与主屏幕“继续阅读”桌面小组件。',
    },
];

const FeaturesAndPreviewsSection = ({
    activeShot,
    onSelectScreenshot,
    onCloseLightbox,
}) => {
    const shotMap = Object.fromEntries(SCREENSHOTS.map((s) => [s.id, s]));
    const einkBookshelf = shotMap['eink-bookshelf'];
    const einkReader = shotMap['eink-reader'];

    // Lightbox navigation across all screenshots
    const currentIndex = activeShot
        ? SCREENSHOTS.findIndex((s) => s.id === activeShot.id)
        : -1;

    const showPrev = useCallback(() => {
        if (currentIndex < 0) return;
        const prevIdx = (currentIndex - 1 + SCREENSHOTS.length) % SCREENSHOTS.length;
        onSelectScreenshot(SCREENSHOTS[prevIdx]);
    }, [currentIndex, onSelectScreenshot]);

    const showNext = useCallback(() => {
        if (currentIndex < 0) return;
        const nextIdx = (currentIndex + 1) % SCREENSHOTS.length;
        onSelectScreenshot(SCREENSHOTS[nextIdx]);
    }, [currentIndex, onSelectScreenshot]);

    useEffect(() => {
        if (!activeShot) return undefined;
        const onKeyDown = (e) => {
            if (e.key === 'Escape') onCloseLightbox();
            if (e.key === 'ArrowLeft') showPrev();
            if (e.key === 'ArrowRight') showNext();
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [activeShot, onCloseLightbox, showPrev, showNext]);

    return (
        <section
            id="features"
            className="py-20 bg-[var(--md-surface-container-low)] m3-dot-pattern"
        >
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="max-w-3xl mb-12">
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-semibold bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)] mb-4">
                        WHAT&rsquo;S NEW IN 2.0 · PIXEL 8 实机一览
                    </span>
                    <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-[var(--md-on-surface)]">
                        从零重写，为轻小说阅读而生
                    </h2>
                    <p className="mt-3 text-base text-[var(--md-on-surface-variant)]">
                        以下全部配图均为 2.0 正式版 Pixel 8 实机截图，点击任意预览图即可全屏查看 1080 × 2400 高清原图。
                    </p>
                </div>

                {/* Unified Feature + Screenshot Showcase Cards */}
                <div className="space-y-7">
                    {SHOWCASE_GROUPS.map((group) => {
                        const Icon = group.icon;
                        const isTwoShots = group.shots.length === 2;

                        return (
                            <article
                                key={group.id}
                                className="rounded-[32px] bg-[var(--md-surface)] border border-[var(--md-outline-variant)]/85 shadow-sm p-6 sm:p-8 lg:p-10"
                            >
                                <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-10 items-center">
                                    {/* Text Block */}
                                    <div
                                        className={`${
                                            isTwoShots ? 'lg:col-span-7' : 'lg:col-span-5'
                                        } ${group.reverseDesktop ? 'lg:order-2' : ''}`}
                                    >
                                        <div
                                            className={`inline-flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-mono font-bold tracking-wide mb-4 ${group.accent}`}
                                        >
                                            <Icon size={14} />
                                            {group.badge}
                                        </div>
                                        <h3 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-[var(--md-on-surface)]">
                                            {group.title}
                                        </h3>
                                        <p className="mt-3 text-sm sm:text-base leading-relaxed text-[var(--md-on-surface-variant)]">
                                            {group.lead}
                                        </p>

                                        <ul className="mt-5 space-y-3 text-sm sm:text-[15px] text-[var(--md-on-surface-variant)]">
                                            {group.bullets.map((bullet) => (
                                                <li
                                                    key={bullet.lead}
                                                    className="flex items-start gap-2.5"
                                                >
                                                    <span
                                                        className={`mt-0.5 w-5 h-5 rounded-full flex items-center justify-center shrink-0 ${group.accent}`}
                                                    >
                                                        <Check size={12} strokeWidth={3} />
                                                    </span>
                                                    <span>
                                                        <strong className="font-semibold text-[var(--md-on-surface)]">
                                                            {bullet.lead}
                                                        </strong>
                                                        ：{bullet.text}
                                                    </span>
                                                </li>
                                            ))}
                                        </ul>
                                    </div>

                                    {/* Screenshots Block */}
                                    <div
                                        className={`${
                                            isTwoShots ? 'lg:col-span-5' : 'lg:col-span-7'
                                        } ${group.reverseDesktop ? 'lg:order-1' : ''}`}
                                    >
                                        <div
                                            className={`grid ${group.shotGridClass} gap-3 sm:gap-4 max-w-2xl mx-auto`}
                                        >
                                            {group.shots.map((item) => {
                                                const shot = shotMap[item.id];
                                                if (!shot) return null;
                                                return (
                                                    <PhonePreviewTile
                                                        key={shot.id}
                                                        shot={shot}
                                                        label={item.label}
                                                        onSelect={onSelectScreenshot}
                                                    />
                                                );
                                            })}
                                        </div>
                                    </div>
                                </div>
                            </article>
                        );
                    })}

                    {/* Authentic E-Ink Mode Specimen Card (Pure White #FFFFFF + 1.5px #111111 Crisp Outlines) */}
                    <article className="rounded-[12px] bg-[#FFFFFF] text-[#000000] border-[1.5px] border-[#111111] p-6 sm:p-8 lg:p-10">
                        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
                            <div className="lg:col-span-7">
                                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-[4px] border border-[#111111] text-xs font-mono font-bold tracking-wider mb-4">
                                    <Tablet size={14} />
                                    E-INK HIGH-CONTRAST MODE
                                </div>
                                <h3 className="text-2xl sm:text-3xl font-extrabold tracking-tight text-[#000000]">
                                    墨水屏高对比模式（电子纸专属适配）
                                </h3>
                                <p className="mt-3 text-sm sm:text-base leading-relaxed text-[#111111]">
                                    针对文石（BOOX）、汉王、掌阅、墨案等开放式 Android 电子纸阅读器深度定制——并非简单的黑白滤镜，而是从主题令牌、组件描边到动画曲线的全局级重塑：
                                </p>
                                <ul className="mt-5 space-y-3 text-sm sm:text-[15px] text-[#000000]">
                                    <li className="flex items-start gap-2.5">
                                        <span className="mt-0.5 w-5 h-5 rounded-[4px] bg-[#000000] text-[#FFFFFF] flex items-center justify-center shrink-0">
                                            <Check size={13} strokeWidth={3} />
                                        </span>
                                        <span>
                                            <strong>纯白底色与纯黑油墨</strong>：全局移除灰阶阴影与半透明表面叠色，采用{' '}
                                            <code className="font-mono text-xs px-1.5 py-0.5 border border-[#111111] rounded-[3px]">
                                                #FFFFFF
                                            </code>{' '}
                                            与{' '}
                                            <code className="font-mono text-xs px-1.5 py-0.5 border border-[#111111] rounded-[3px]">
                                                #000000
                                            </code>
                                            ，杜绝 16 阶灰度抖动发虚。
                                        </span>
                                    </li>
                                    <li className="flex items-start gap-2.5">
                                        <span className="mt-0.5 w-5 h-5 rounded-[4px] bg-[#000000] text-[#FFFFFF] flex items-center justify-center shrink-0">
                                            <Check size={13} strokeWidth={3} />
                                        </span>
                                        <span>
                                            <strong>全局 1dp 实体描边</strong>：卡片、搜索框、标签、工具栏与底部导航栏自动切换为锐利实体黑框，层级清晰分明。
                                        </span>
                                    </li>
                                    <li className="flex items-start gap-2.5">
                                        <span className="mt-0.5 w-5 h-5 rounded-[4px] bg-[#000000] text-[#FFFFFF] flex items-center justify-center shrink-0">
                                            <Check size={13} strokeWidth={3} />
                                        </span>
                                        <span>
                                            <strong>零残影瞬切翻页</strong>：全局关闭过渡动画，配合“墨水屏极速”无动画翻页与物理音量键支持，大幅减少刷新闪烁。
                                        </span>
                                    </li>
                                </ul>
                            </div>

                            <div className="lg:col-span-5 flex justify-center gap-4">
                                {[einkBookshelf, einkReader].filter(Boolean).map((shot) => (
                                    <button
                                        key={shot.id}
                                        type="button"
                                        onClick={() => onSelectScreenshot(shot)}
                                        aria-label={`全屏查看：${shot.title}`}
                                        className="group w-1/2 max-w-[195px] rounded-[8px] border-[1.5px] border-[#111111] bg-[#FFFFFF] p-1.5 text-left cursor-pointer hover:bg-[#F2F2F2]"
                                    >
                                        <img
                                            src={shot.src}
                                            alt={shot.title}
                                            loading="lazy"
                                            className="w-full h-auto rounded-[4px] border border-[#111111]/30 block"
                                        />
                                        <div className="mt-2 px-1 flex items-center justify-between text-xs font-semibold text-[#000000]">
                                            <span className="truncate">
                                                {shot.title.replace('墨水屏高对比 · ', '')}
                                            </span>
                                            <Maximize2 size={12} className="shrink-0" />
                                        </div>
                                    </button>
                                ))}
                            </div>
                        </div>
                    </article>
                </div>

                {/* Compact Under-the-Hood v2.0 Capabilities Strip */}
                <div className="mt-7 grid grid-cols-1 md:grid-cols-3 gap-5">
                    {INFRA_HIGHLIGHTS.map((item) => {
                        const Icon = item.icon;
                        return (
                            <div
                                key={item.title}
                                className="rounded-[24px] p-6 bg-[var(--md-surface)] border border-[var(--md-outline-variant)]/80 flex flex-col"
                            >
                                <div className="w-10 h-10 rounded-2xl bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)] flex items-center justify-center mb-4">
                                    <Icon size={20} />
                                </div>
                                <h3 className="text-base font-bold text-[var(--md-on-surface)] mb-2">
                                    {item.title}
                                </h3>
                                <p className="text-sm leading-relaxed text-[var(--md-on-surface-variant)]">
                                    {item.description}
                                </p>
                            </div>
                        );
                    })}
                </div>
            </div>

            {/* Shared Expressive Lightbox Modal */}
            {activeShot && (
                <div
                    role="dialog"
                    aria-modal="true"
                    aria-label={activeShot.title}
                    className="fixed inset-0 z-[80] flex flex-col items-center justify-center bg-black/88 backdrop-blur-md p-4 sm:p-6"
                    onClick={onCloseLightbox}
                >
                    <div
                        className="relative max-w-4xl w-full flex flex-col items-center"
                        onClick={(e) => e.stopPropagation()}
                    >
                        <div className="w-full flex items-center justify-between mb-3 text-white">
                            <span className="px-3.5 py-1 rounded-full bg-white/15 text-xs font-mono">
                                {currentIndex + 1} / {SCREENSHOTS.length}
                            </span>
                            <button
                                type="button"
                                onClick={onCloseLightbox}
                                aria-label="关闭预览"
                                className="p-2.5 rounded-full bg-white/15 hover:bg-white/25 text-white m3-emphasized cursor-pointer"
                            >
                                <X size={20} />
                            </button>
                        </div>

                        <div className="relative flex items-center justify-center w-full">
                            <button
                                type="button"
                                onClick={showPrev}
                                aria-label="上一张截图"
                                className="absolute left-0 sm:left-4 z-10 p-3 rounded-full bg-white/15 hover:bg-white/30 text-white backdrop-blur-md m3-spring hover:scale-110 cursor-pointer"
                            >
                                <ChevronLeft size={24} />
                            </button>

                            <img
                                src={activeShot.src}
                                alt={activeShot.title}
                                className="max-h-[75vh] w-auto rounded-[24px] shadow-2xl border border-white/15"
                            />

                            <button
                                type="button"
                                onClick={showNext}
                                aria-label="下一张截图"
                                className="absolute right-0 sm:right-4 z-10 p-3 rounded-full bg-white/15 hover:bg-white/30 text-white backdrop-blur-md m3-spring hover:scale-110 cursor-pointer"
                            >
                                <ChevronRight size={24} />
                            </button>
                        </div>

                        <div className="mt-4 max-w-xl text-center px-5 py-3 rounded-[22px] bg-white/12 backdrop-blur-md text-white">
                            <p className="font-bold text-sm sm:text-base">{activeShot.title}</p>
                            <p className="mt-0.5 text-xs sm:text-sm text-white/80">
                                {activeShot.caption}
                            </p>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
};

// --- Dev Log Cards & Modal Sheet Drawer ---

const BlogCard = ({ post, compact = false, className = '' }) => {
    const [likes, setLikes] = useState(post.likes);
    const [liked, setLiked] = useState(false);

    const handleLike = () => {
        setLikes((prev) => (liked ? prev - 1 : prev + 1));
        setLiked((prev) => !prev);
    };

    return (
        <article
            className={`rounded-[24px] bg-[var(--md-surface)] border border-[var(--md-outline-variant)]/85 m3-emphasized hover:border-[var(--md-primary)]/45 flex flex-col ${
                compact ? 'p-5' : 'p-6'
            } ${className}`}
        >
            <div className="flex items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)] flex items-center justify-center font-bold text-sm shrink-0">
                        {post.author[0]}
                    </div>
                    <div>
                        <h3 className="font-bold text-sm text-[var(--md-on-surface)]">
                            {post.author}
                        </h3>
                        <div className="flex items-center text-xs text-[var(--md-on-surface-variant)]">
                            <Calendar size={11} className="mr-1" />
                            {post.date}
                        </div>
                    </div>
                </div>
                <span className="text-[11px] font-mono px-2.5 py-0.5 rounded-full bg-[var(--md-surface-container)] text-[var(--md-on-surface-variant)]">
                    #{post.id}
                </span>
            </div>

            <div className="flex-grow mb-4">
                <PostParagraphs paragraphs={post.paragraphs} />
            </div>

            <div className="pt-3 border-t border-[var(--md-outline-variant)]/60 flex items-center justify-between">
                <button
                    type="button"
                    onClick={handleLike}
                    aria-pressed={liked}
                    className={`inline-flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-full m3-spring cursor-pointer ${
                        liked
                            ? 'bg-rose-500/15 text-rose-500'
                            : 'bg-[var(--md-surface-container)] text-[var(--md-on-surface-variant)] hover:text-rose-500'
                    }`}
                >
                    <Heart size={14} fill={liked ? 'currentColor' : 'none'} />
                    <span>{likes}</span>
                </button>
                <span className="text-[11px] text-[var(--md-on-surface-variant)] opacity-70">
                    历史开发碎碎念
                </span>
            </div>
        </article>
    );
};

const NewsDrawer = ({ isOpen, onClose }) => {
    useEffect(() => {
        if (!isOpen) return undefined;
        const onKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [isOpen, onClose]);

    return (
        <>
            <div
                aria-hidden="true"
                className={`fixed inset-0 z-[60] bg-black/50 backdrop-blur-sm transition-opacity duration-300 ${
                    isOpen ? 'opacity-100' : 'opacity-0 pointer-events-none'
                }`}
                onClick={onClose}
            />

            <aside
                role="dialog"
                aria-modal="true"
                aria-label="完整开发日志"
                className={`fixed top-0 right-0 z-[70] h-full w-full sm:w-[520px] rounded-none sm:rounded-l-[32px] shadow-2xl transform transition-transform duration-300 bg-[var(--md-surface-container-low)] border-l border-[var(--md-outline-variant)] ${
                    isOpen ? 'translate-x-0' : 'translate-x-full'
                }`}
            >
                <div className="flex flex-col h-full">
                    <div className="flex items-center justify-between p-6 border-b border-[var(--md-outline-variant)]/70">
                        <div className="flex items-center gap-3">
                            <div className="p-2.5 rounded-2xl bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)]">
                                <History size={22} />
                            </div>
                            <div>
                                <h2 className="text-xl font-bold text-[var(--md-on-surface)]">
                                    完整开发日志
                                </h2>
                                <p className="text-xs text-[var(--md-on-surface-variant)]">
                                    共 {BLOG_POSTS.length} 条记录（2014 — 2025）
                                </p>
                            </div>
                        </div>
                        <button
                            type="button"
                            onClick={onClose}
                            aria-label="关闭开发日志"
                            className="p-2.5 rounded-full bg-[var(--md-surface-container-high)] text-[var(--md-on-surface)] hover:bg-[var(--md-primary-container)] cursor-pointer"
                        >
                            <X size={20} />
                        </button>
                    </div>

                    <div className="flex-1 overflow-y-auto p-5 space-y-4">
                        {BLOG_POSTS.map((post) => (
                            <BlogCard key={post.id} post={post} compact={true} />
                        ))}

                        <div className="text-center py-6">
                            <p className="text-sm font-medium text-[var(--md-on-surface-variant)]">
                                已显示全部历史记录
                            </p>
                            <p className="text-xs mt-1 text-[var(--md-on-surface-variant)] opacity-70">
                                感谢一路相伴！
                            </p>
                        </div>
                    </div>
                </div>
            </aside>
        </>
    );
};

const NewsSection = ({ onOpenDrawer }) => {
    const recentPosts = BLOG_POSTS.slice(0, 3);

    return (
        <section id="news" className="py-20 bg-[var(--md-background)]">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="mb-10">
                    <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-semibold bg-[var(--md-secondary-container)] text-[var(--md-on-secondary-container)] mb-3">
                        DEVLOG ARCHIVE · 2014 — 2025
                    </span>
                    <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-[var(--md-on-surface)]">
                        开发日志
                    </h2>
                    <p className="mt-2 text-sm sm:text-base text-[var(--md-on-surface-variant)]">
                        完整保留自 2014 年起共 {BLOG_POSTS.length} 条开发者手记与历史更新记录。
                    </p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-5 mb-10">
                    {recentPosts.map((post) => (
                        <BlogCard key={post.id} post={post} compact={true} className="h-full" />
                    ))}
                </div>

                <div className="text-center">
                    <button
                        type="button"
                        onClick={onOpenDrawer}
                        className="group inline-flex items-center gap-2.5 px-8 py-4 rounded-full bg-[var(--md-primary)] text-[var(--md-on-primary)] text-base font-bold shadow-lg shadow-blue-600/20 m3-spring hover:scale-[1.03] hover:rounded-[20px] cursor-pointer"
                    >
                        查看完整更新记录（共 {BLOG_POSTS.length} 条）
                        <ArrowRight
                            size={18}
                            className="m3-spring group-hover:translate-x-1"
                        />
                    </button>
                </div>
            </div>
        </section>
    );
};

// --- Staff & v2.0 Credits Section ---

const StaffSection = () => (
    <section id="staff" className="py-20 bg-[var(--md-surface-container-low)]">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="text-center max-w-2xl mx-auto mb-12">
                <span className="inline-flex items-center gap-1.5 px-3.5 py-1 rounded-full text-xs font-semibold bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)] mb-3">
                    TEAM &amp; CREDITS
                </span>
                <h2 className="text-3xl sm:text-4xl font-extrabold tracking-tight text-[var(--md-on-surface)]">
                    制作团队与致谢
                </h2>
            </div>

            {/* Core Team Cards */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6 max-w-5xl mx-auto">
                {STAFF.map((member) => (
                    <div
                        key={member.name}
                        className="flex flex-col items-center text-center p-7 rounded-[28px] bg-[var(--md-surface)] border border-[var(--md-outline-variant)]/85 m3-spring hover:-translate-y-1.5 hover:border-[var(--md-primary)]/45"
                    >
                        <div className="relative mb-4">
                            <div className="w-24 h-24 rounded-full overflow-hidden ring-4 ring-[var(--md-primary-container)]">
                                <img
                                    src={member.avatar}
                                    alt={member.name}
                                    className="w-full h-full object-cover"
                                />
                            </div>
                        </div>
                        <h3 className="text-lg font-bold text-[var(--md-on-surface)]">
                            {member.name}
                        </h3>
                        <p className="mt-1 text-xs font-semibold px-3 py-1 rounded-full bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)]">
                            {member.role}
                        </p>
                        <p className="mt-3 text-sm leading-relaxed text-[var(--md-on-surface-variant)]">
                            {member.bio}
                        </p>
                        {member.link && (
                            <a
                                href={member.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-[var(--md-primary)] hover:underline"
                            >
                                访问主页 <ChevronRight size={13} />
                            </a>
                        )}
                    </div>
                ))}
            </div>

            {/* v2.0 Vibe Coding & Community Credits Strip */}
            <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-5 max-w-5xl mx-auto">
                {COMMUNITY_CREDITS.map((credit) => (
                    <div
                        key={credit.title}
                        className="rounded-[24px] p-5 bg-[var(--md-surface)] border border-[var(--md-outline-variant)]/75 flex flex-col justify-between"
                    >
                        <div>
                            <span className="inline-block px-2.5 py-0.5 rounded-full text-[11px] font-mono font-semibold bg-[var(--md-surface-container)] text-[var(--md-primary)] border border-[var(--md-outline-variant)] mb-3">
                                {credit.tag}
                            </span>
                            <h3 className="font-bold text-base text-[var(--md-on-surface)] mb-2">
                                {credit.title}
                            </h3>
                            <p className="text-xs sm:text-sm leading-relaxed text-[var(--md-on-surface-variant)]">
                                {credit.description}
                            </p>
                        </div>

                        {credit.link && (
                            <a
                                href={credit.link}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="mt-4 inline-flex items-center gap-1 text-xs font-semibold text-[var(--md-primary)] hover:underline"
                            >
                                {credit.linkLabel} <ExternalLink size={12} />
                            </a>
                        )}

                        {credit.links && (
                            <div className="mt-4 flex flex-wrap gap-3">
                                {credit.links.map((item) => (
                                    <a
                                        key={item.href}
                                        href={item.href}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="inline-flex items-center gap-1 text-xs font-semibold text-[var(--md-primary)] hover:underline"
                                    >
                                        {item.label} <ExternalLink size={12} />
                                    </a>
                                ))}
                            </div>
                        )}
                    </div>
                ))}
            </div>
        </div>
    </section>
);

// --- Disqus Comments Section (Preserves existing thread URL & identifier) ---

const DisqusComments = () => {
    useEffect(() => {
        window.disqus_config = function () {
            this.page.url = 'https://wenku8.app/';
            this.page.identifier = 'wenku8android';
        };

        const d = document;
        if (!d.getElementById('disqus-embed-script')) {
            const s = d.createElement('script');
            s.id = 'disqus-embed-script';
            s.src = 'https://wenku8android.disqus.com/embed.js';
            s.setAttribute('data-timestamp', String(+new Date()));
            (d.head || d.body).appendChild(s);
        }

        // Remove injected Disqus ad iframes while keeping the comment thread iframe.
        const disqusThread = d.getElementById('disqus_thread');
        if (disqusThread) {
            const observer = new MutationObserver(() => {
                const iframes = disqusThread.getElementsByTagName('iframe');
                if (iframes.length > 1) {
                    const commentsIframe = iframes[1];
                    disqusThread.replaceChildren(commentsIframe);
                    observer.disconnect();
                }
            });
            observer.observe(disqusThread, { childList: true, subtree: true });
            return () => observer.disconnect();
        }
        return undefined;
    }, []);

    return (
        <section id="comments" className="py-20 bg-[var(--md-background)]">
            <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center gap-3 mb-8">
                    <div className="p-2.5 rounded-2xl bg-[var(--md-primary-container)] text-[var(--md-on-primary-container)]">
                        <MessageSquare size={22} />
                    </div>
                    <div>
                        <h2 className="text-2xl sm:text-3xl font-extrabold text-[var(--md-on-surface)]">
                            留言板
                        </h2>
                        <p className="text-xs sm:text-sm text-[var(--md-on-surface-variant)]">
                            欢迎交流 2.0 体验建议，或前往 GitHub Issues / Telegram 群组反馈
                        </p>
                    </div>
                </div>

                <div className="p-6 md:p-8 rounded-[28px] bg-[var(--md-surface)] border border-[var(--md-outline-variant)] shadow-sm">
                    <div id="disqus_thread" />
                    <noscript>
                        Please enable JavaScript to view the{' '}
                        <a
                            href="https://disqus.com/?ref_noscript"
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            comments powered by Disqus.
                        </a>
                    </noscript>
                </div>
            </div>
        </section>
    );
};

// --- Footer ---

const Footer = () => (
    <footer className="py-12 bg-[var(--md-surface-container)] border-t border-[var(--md-outline-variant)]/80">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
            <div className="inline-flex items-center gap-2.5 mb-3">
                <img
                    src="imgs/app/app_icon.png"
                    alt=""
                    aria-hidden="true"
                    className="w-6 h-6 object-contain"
                />
                <span className="font-bold text-base text-[var(--md-on-surface)]">
                    轻小说文库 Android 2.0
                </span>
            </div>
            <p className="text-sm text-[var(--md-on-surface-variant)]">
                &copy; 2014-2027 MewX &amp; Contributors. 保留所有权利。
            </p>
            <p className="mt-2 text-xs text-[var(--md-on-surface-variant)] opacity-80">
                本项目是社区开源粉丝作品，与轻小说文库 (Wenku8.com) 无官方关联。
            </p>
            <div className="mt-4 flex flex-wrap justify-center gap-4 text-xs font-medium text-[var(--md-on-surface-variant)]">
                <a
                    href="https://github.com/MewX/light-novel-library_Wenku8_Android"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-[var(--md-primary)] underline underline-offset-2"
                >
                    GitHub 开源仓库
                </a>
                <a
                    href="https://t.me/wk8app"
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:text-[var(--md-primary)] underline underline-offset-2"
                >
                    Telegram 群组
                </a>
                <a
                    href="/privacy/"
                    className="hover:text-[var(--md-primary)] underline underline-offset-2"
                >
                    隐私政策 / Privacy Policy
                </a>
            </div>
        </div>
    </footer>
);

// --- Root App ---

const App = () => {
    const [darkMode, setDarkMode] = useState(
        () =>
            typeof window !== 'undefined' &&
            Boolean(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches),
    );
    const [isNewsDrawerOpen, setIsNewsDrawerOpen] = useState(false);
    const [activeScreenshot, setActiveScreenshot] = useState(null);

    const toggleDarkMode = () => setDarkMode((prev) => !prev);

    return (
        <div
            className={`${
                darkMode ? 'theme-dark dark' : ''
            } min-h-screen bg-[var(--md-background)] text-[var(--md-on-background)] transition-colors duration-300`}
        >
            <Navbar darkMode={darkMode} toggleDarkMode={toggleDarkMode} />
            <main>
                <Hero onSelectScreenshot={setActiveScreenshot} />
                <FeaturesAndPreviewsSection
                    activeShot={activeScreenshot}
                    onSelectScreenshot={setActiveScreenshot}
                    onCloseLightbox={() => setActiveScreenshot(null)}
                />
                <NewsSection onOpenDrawer={() => setIsNewsDrawerOpen(true)} />
                <StaffSection />
                <DisqusComments />
            </main>
            <Footer />

            <NewsDrawer
                isOpen={isNewsDrawerOpen}
                onClose={() => setIsNewsDrawerOpen(false)}
            />
        </div>
    );
};

export default App;

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Link, NavLink, Route, Switch, useLocation } from 'react-router-dom';
import Select, { components, SingleValue } from 'react-select';
import * as H from 'history';

import '../../../node_modules/intro.js/introjs.css';
import * as api from '../../api';

import { ContentNavLink, ContentNavPosition, EntityName, getEntityNames, getMenuTree, getSearchNodes, MenuEntry } from "../../api";
import { Search } from "../search/Search";
import { AttributePage } from "../view/AttributePage";
import { View } from "../view/View";
import './App.scss';

import { ShortUrl } from "../shortUrl/ShortUrl";
import { Statistics } from "../statistics/Statistics";
import { Synopsis } from "../synopsis/Synopsis";

import { Booklet } from '../booklet/Booklet';

import { StaticContent } from '../static/StaticContent';
import { LangContextType } from '../../translation/Translation';

const LangContext = React.createContext<LangContextType>({ lang: "de", overrides: {} });
export default LangContext;

export function App() {
  type LangOption = { value: string, label: string };
  const [searches, setSearches] = useState<EntityName[]>([]);
  const [searchNodes, setSearchNodes] = useState<EntityName[]>([]);
  const [menuTree, setMenuTree] = useState<MenuEntry[]>([]);
  const [shortUrls, setShortUrls] = useState<api.ShortUrlEntitys[]>([]);
  const [language, setLanguage] = useState<string>("de");
  const [allLanguages, setAllLanguages] = useState<string[]>(["de"]);
  const [contents, setContents] = useState<api.Contents>({ contents: [] });

  const getContentByUri = (uri: string): string =>
    getContentByUriOrElse(uri, "");

  const getContentByUriOrElse = (uri: string, fallback: string): string => {
    const cont = contents.contents.find(c => c.uri === uri);
    return cont?.content || fallback;
  }

  useEffect(() => {
    (async () => {
      const languages = await api.getLanguages();
      setAllLanguages(languages);
    })()
  }, [])

  useEffect(() => {
    const initialRequests = [
      getEntityNames(language).then(setSearches),
      getSearchNodes(language).then(setSearchNodes),
      getMenuTree(language).then(setMenuTree),
      api.getContentsByLang(language).then(setContents),
    ];
    // Loading the document description takes a while; warm the cache once the
    // page itself is there, so opening a search doesn't have to wait for it.
    Promise.allSettled(initialRequests)
      .then(() => api.getEntityDescription("http://monodicum/document", language))
      .catch(() => { /* failed prefetch isn't cached; the next caller retries */ });
  }, [language])

  useEffect(() => {
    api.removeLegacyEntityDescriptionCache();
  }, [])

  useEffect(() => {
    if (allLanguages.length == 1) {
      console.debug("Only one language available, setting to", allLanguages[0]);
      setLanguage(allLanguages[0]);
    } else if (allLanguages.length == 0) {
      console.debug("No tagged language found, setting to 'de'");
      setLanguage("de");
    } else {
      console.debug("Available languages", allLanguages);
      const savedLang = localStorage.getItem('corpusMonodicumLang');
      if (savedLang === null) {
        const lang = navigator.language;
        if (lang.indexOf("en") >= 0) {
          setLanguage("en");
        } else if (lang.indexOf("fr") >= 0) {
          setLanguage("fr");
        } else {
          setLanguage("de");
        }
      } else {
        setLanguage(savedLang);
      }
    }
  }, [allLanguages])

  const langContext: LangContextType = useMemo(() => { return { lang: language, overrides: {} }; }, [language])


  useEffect(() => {
    (async () => {
      const shortUrlEntitys = await api.getShortUrlEntitys(language);
      setShortUrls(shortUrlEntitys);
    })()
  }, [language])

  const onLangSelect = (e: string | undefined) => {
    if (e) {
      const newLang = e;
      setLanguage(newLang);
      localStorage.setItem('corpusMonodicumLang', newLang);
    }
  }

  useEffect(() => {
    document.title = contents.contents.find(c => c.uri === "http://olyro.de/mondiview/tabTitle")?.content || "Corpus Monodicum"
  }, [contents])

  const renderInfoButton = () => {
    const target = getContentByUriOrElse("http://olyro.de/mondiview/infoButtonLink", "/userInstructions")
    const title = getContentByUriOrElse("http://olyro.de/mondiview/infoButtonTitle", "Info")
    const icon =
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
        <path id="circle" fill="#FFFFFF" d="m12 2.085c-5.477 0-9.915 4.438-9.915 9.916 0 5.48 4.438 9.92 9.916 9.92 5.48 0 9.92-4.44 9.92-9.913 0-5.477-4.44-9.915-9.913-9.915zm.002 18a8.084 8.084 0 1 1 0 -16.17 8.084 8.084 0 0 1 0 16.17z" />
        <path id="info" transform="translate(0,1)" fill="#FFFFFF" d="m11 6.16v2.01h2.02v-2.01zm-1.61 3.22v2.01h1.61v4.43h-1.61v2.01h5.23v-2.01h-1.61v-6.44z" />
      </svg>
    if (target.startsWith("http")) {
      return <a title={title} href={target}>{icon}</a>
    } else {
      return <NavLink title={title} activeClassName="active" to={target}>{icon}</NavLink>
    }
  }

  const renderLanguageSelect = () => {
    if (allLanguages.length <= 1) {
      return <></>;
    }

    const langOptions: LangOption[] = allLanguages.map((lang) => { return { value: lang, label: lang.toUpperCase() } })
    const CustomValue = (_children: any, _props: any) => {
      const label = langOptions.find(a => a.value === language)?.label;

      return (
        <div className="langOption">
          <div className={"flagSelected flag-" + language}></div>
          <span>{label}</span>
        </div>
      )
    }

    const CustomOption = (props: any) => {
      return (
        <div className="langOption">
          <div className={"flag flag-" + props.data.value}></div>
          <components.Option {...props} />
        </div>
      )
    }

    return (
      <Select className="lang-select" options={langOptions} components={{ Option: CustomOption, ValueContainer: CustomValue }} onChange={(e: SingleValue<LangOption>) => onLangSelect(e?.value)} />
    )
  }

  const navLinks = useMemo(
    () => contents.contents.filter((c): c is { uri: string, content?: string, navLink: ContentNavLink } => c.navLink !== undefined),
    [contents]
  )
  const staticRoutesInfo = useMemo(
    () => ({ fixed: navLinks.filter(c => !isExternalRoute(c.navLink.route)).map(c => c.navLink.route), shortUrlTags: shortUrls.map(u => u.tag) }),
    [navLinks, shortUrls]
  )
  // Everything routable under /search/<uri>: real classes (resolved as plain class
  // searches) plus :Search nodes (resolved by type in Search.tsx).
  const routableEntities = [...searches, ...searchNodes]
  const navLinksForPosition = (pos: keyof ContentNavPosition) => {
    return navLinks.filter(c => c.navLink.position[pos] !== undefined)
      .sort((a, b) => a.navLink.position[pos]! - b.navLink.position[pos]!)
  }

  const location = useLocation()
  useEffect(() => {
    window.postMessage({ "location": location.pathname, "search": location.search });
  }, [location])

  return (
    <div id="app-main" className="app-main">
      <header className="header">
        <NavLink to="/" className="title">
          <span>{getContentByUriOrElse("http://olyro.de/mondiview/headerTitle", "CORPUS MONODICUM")}</span>
        </NavLink>
        <div className="navigation">
          <>
            <NavMenu entries={menuTree} />
            {searches.slice().sort((a, b) => (a.orderBy ?? a.name).localeCompare((b.orderBy ?? b.name))).map(s =>
              <NavLink isActive={navLinkIsActive(s.uri)} key={s.uri} activeClassName="active" to={addNavLinkActiveHint(s.alternativeLink ? s.alternativeLink : searchLink(s), s.uri)}>{s.name}</NavLink>
            )}
            {navLinksForPosition("left")
              .map(c => isExternalRoute(c.navLink.route)
                ? <a key={c.uri} href={c.navLink.route} target="_blank" rel="noopener noreferrer">{c.navLink.title}</a>
                : <NavLink key={c.uri} activeClassName="active" to={"/" + c.navLink.route}>{c.navLink.title}</NavLink>
              )
            }
            <span className="filler" />
            {navLinksForPosition("right")
              .map(c => isExternalRoute(c.navLink.route)
                ? <a key={c.uri} href={c.navLink.route} target="_blank" rel="noopener noreferrer">{c.navLink.title}</a>
                : <NavLink key={c.uri} activeClassName="active" to={"/" + c.navLink.route}>{c.navLink.title}</NavLink>
              )
            }
          </>
          {renderInfoButton()}
          {renderLanguageSelect()}
        </div>
      </header>
      <div id="app-router-outlet">
        <LangContext.Provider value={langContext}>
          <Switch>
            {routableEntities.map(s =>
              <Route key={s.uri} path={searchLink(s) + ":query?"}>
                <Search uri={s.uri} staticRoutes={staticRoutesInfo} />
              </Route>
            )}
            {routableEntities.map(s =>
              <Route key={"c1_" + s.uri} path={compareSearch1(s)}>
                <Search uri={s.uri} staticRoutes={staticRoutesInfo} />
              </Route>
            )}
            {routableEntities.map(s =>
              <Route key={"c2_" + s.uri} path={compareSearch2(s)}>
                <Search uri={s.uri} staticRoutes={staticRoutesInfo} />
              </Route>
            )}
            <Route path="/view">
              <View staticRoutes={staticRoutesInfo} />
            </Route>
            <Route path="/attribute">
              <AttributePage staticRoutes={staticRoutesInfo} />
            </Route>
            {shortUrls.map(u =>
              <Route key={u.uri} path={"/" + u.tag + "/:id"}><ShortUrl shortEntity={u} /></Route>
            )}
            {navLinks.filter(c => !isExternalRoute(c.navLink.route)).map(c =>
              <Route key={c.uri} path={"/" + c.navLink.route}>
                <StaticContent className={c.navLink.route + "-main"} innerHtml={getContentByUri(c.uri)} staticRoutes={staticRoutesInfo} />
              </Route>
            )}
            <Route path="/synopsis/:data?"><Synopsis staticRoutes={staticRoutesInfo} /></Route>
            <Route path="/statistics/:data?"><Statistics /></Route>
            <Route path="/print"><Booklet isEditor={false} /></Route>
            <Route path="/printEditor"><Booklet isEditor={true} /></Route>
            <Route path="/">
              <div className="hero-container">
              </div>
              <div className="cm-main">
                <StaticContent className="cm-main_pre-links" innerHtml={getContentByUri("http://olyro.de/mondiview/mainPagePreSearches")} staticRoutes={staticRoutesInfo} />
                <div className="cm-main_links">
                  {flattenTargeted(menuTree).map(e => {
                    const link = menuEntryLink(e)
                    if (!link) return null
                    return link.external
                      ? <a key={e.uri} href={link.to}>{e.label}</a>
                      : <Link key={e.uri} to={link.to}>{e.label}</Link>
                  })}
                  {searches.slice().sort((a, b) => (a.orderBy ?? a.name).localeCompare((b.orderBy ?? b.name))).map(s =>
                    <Link key={s.uri} to={s.alternativeLink ? s.alternativeLink : searchLink(s)}>{s.name}</Link>
                  )}
                  {navLinksForPosition("left")
                    .map(c => isExternalRoute(c.navLink.route)
                      ? <a key={c.uri} href={c.navLink.route} target="_blank" rel="noopener noreferrer">{c.navLink.title}</a>
                      : <Link key={c.uri} to={"/" + c.navLink.route}>{c.navLink.title}</Link>
                    )
                  }
                </div>
                <StaticContent className="cm-main_post-links" innerHtml={getContentByUri("http://olyro.de/mondiview/mainPagePostSearches")} staticRoutes={staticRoutesInfo} />
              </div>

            </Route>
          </Switch>
        </LangContext.Provider>
      </div>
      <footer className="footer">
        <div>
          {getContentByUriOrElse("http://olyro.de/mondiview/copyright", "© 2020-2024 Corpus Monodicum")}
          &nbsp;| {getContentByUriOrElse("http://olyro.de/mondiview/footer", "")}
          {navLinksForPosition("footer")
            .map(c => <span key={c.uri}> | {isExternalRoute(c.navLink.route)
              ? <a href={c.navLink.route} target="_blank" rel="noopener noreferrer">{c.navLink.title}</a>
              : <NavLink activeClassName="active" to={"/" + c.navLink.route}>{c.navLink.title}</NavLink>
            }</span>)
          }
        </div>
      </footer>
    </div>
  );
}
//<NavLink activeClassName="active" to="/foerdervermerk">{translate("funding")}</NavLink> | <NavLink activeClassName="active" to="/datenschutz">{translate("privacy")}</NavLink> | <NavLink activeClassName="active" to="/feedback">{translate("feedback")}</NavLink>

function isExternalRoute(route: string): boolean {
  return route.startsWith("http://") || route.startsWith("https://");
}

function compareSearch1(s: EntityName): string {
  return "/searchCompare/" + encodeURIComponent(s.uri) + "/:query/:doc";
}

function compareSearch2(s: EntityName): string {
  return "/searchCompare/" + encodeURIComponent(s.uri) + "/:doc";
}

function searchLink(s: EntityName, suffix?: string): string {
  return "/search/" + encodeURIComponent(s.uri) + "/" + (suffix || "");
}

function addNavLinkActiveHint(uri: string, entityUri: string): string {
  const addChar = uri.includes("?") ? "&" : "?";
  const addition = "navLinkHint=" + encodeURIComponent(entityUri);
  return uri + addChar + addition;
}

function navLinkIsActive(entityUri: string) {
  return (_: any, location: H.Location): boolean => {
    const search = new URLSearchParams(location.search);
    return search.get("navLinkHint") === entityUri;
  }
}

// Resolve a menu entry's target to a link. `null` means no target (a pure grouping
// node). navLinkHint (= the entry uri) drives highlighting, so two entries over the
// same search stay independently active.
function menuEntryLink(entry: MenuEntry): { to: string, external: boolean } | null {
  const t = entry.target;
  if (!t) return null;
  switch (t.kind) {
    case "search":
      return { to: addNavLinkActiveHint("/search/" + encodeURIComponent(t.uri) + "/", entry.uri), external: false };
    case "href":
      return { to: t.url, external: t.url.startsWith("http") };
  }
}

function NavMenu({ entries }: { entries: MenuEntry[] }): React.ReactElement {
  const [openUris, setOpenUris] = useState<Set<string>>(new Set());
  const ref = useRef<HTMLDivElement>(null);
  const location = useLocation();
  const activeHint = new URLSearchParams(location.search).get("navLinkHint");

  const toggle = (uri: string) => setOpenUris(prev => {
    const next = new Set(prev);
    if (next.has(uri)) next.delete(uri); else next.add(uri);
    return next;
  });
  const closeAll = () => setOpenUris(new Set());

  useEffect(() => {
    if (openUris.size === 0) return;
    const onDocMouseDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) closeAll();
    };
    document.addEventListener("mousedown", onDocMouseDown);
    return () => document.removeEventListener("mousedown", onDocMouseDown);
  }, [openUris]);

  return (
    <div className="nav-menu" ref={ref}>
      {entries.map(e => <MenuNode key={e.uri} entry={e} activeHint={activeHint} openUris={openUris} toggle={toggle} closeAll={closeAll} />)}
    </div>
  );
}

// True if any descendant of `entry` is the currently-active nav target (its uri
// matches the active navLinkHint). Used to bold a group whose sub-item is active.
function hasActiveDescendant(entry: MenuEntry, activeHint: string | null): boolean {
  if (!activeHint) return false;
  return entry.children.some(c => c.uri === activeHint || hasActiveDescendant(c, activeHint));
}

function MenuNode({ entry, activeHint, openUris, toggle, closeAll }: {
  entry: MenuEntry, activeHint: string | null, openUris: Set<string>, toggle: (uri: string) => void, closeAll: () => void
}): React.ReactElement {
  if (entry.children.length === 0) {
    const link = menuEntryLink(entry);
    if (link === null) return <span className="menu-label">{entry.label}</span>;
    return link.external
      ? <a className="menu-label" href={link.to} onClick={closeAll}>{entry.label}</a>
      : <NavLink className="menu-label" isActive={navLinkIsActive(entry.uri)} activeClassName="active" to={link.to} onClick={closeAll}>{entry.label}</NavLink>;
  }

  const open = openUris.has(entry.uri);
  const childActive = hasActiveDescendant(entry, activeHint);
  return (
    <div className={"menu-group" + (open ? " open" : "")}>
      <button type="button" className={"menu-label menu-toggle" + (childActive ? " active-parent" : "")} aria-expanded={open} onClick={() => toggle(entry.uri)}>
        {entry.label}
      </button>
      <ul className="menu-dropdown">
        {entry.children.map(child => <li key={child.uri}><MenuNode entry={child} activeHint={activeHint} openUris={openUris} toggle={toggle} closeAll={closeAll} /></li>)}
      </ul>
    </div>
  );
}

// All entries carrying a target, depth-first — used for the flat link list on the
// main page (groups are flattened to their targeted descendants).
function flattenTargeted(entries: MenuEntry[]): MenuEntry[] {
  return entries.flatMap(e => [...(e.target ? [e] : []), ...flattenTargeted(e.children)]);
}

import { createElement, ReactHTML, useEffect, useLayoutEffect, useRef } from 'react';
import { useHistory } from 'react-router-dom';

type StaticContentProps = {
  innerHtml: string,
  /** all NavLink routes for any content, for replacing links in the HTML */
  staticRoutes: StaticRoutes,
  tag?: (keyof ReactHTML),
} & React.DetailedHTMLProps<React.HTMLAttributes<HTMLElement>, HTMLElement>;

export type StaticRoutes = { fixed: string[], shortUrlTags: string[] }

export function StaticContent({ innerHtml, staticRoutes, tag, ...tagAttrs }: StaticContentProps) {
  const contentRef = useRef<HTMLDivElement | null>(null)
  const history = useHistory();

  // The content goes in via createContextualFragment instead of
  // dangerouslySetInnerHTML: fragment-parsed <script> elements are not marked
  // "already started", so scripts embedded in the (team-authored) content
  // execute — see ADR 0007. Scripts re-run on every mount, so they must be
  // idempotent. useLayoutEffect keeps the content in place before first paint.
  useLayoutEffect(() => {
    if (contentRef.current == null) return;
    const range = document.createRange();
    range.selectNodeContents(contentRef.current);
    contentRef.current.replaceChildren(range.createContextualFragment(innerHtml));
  }, [innerHtml])

  useEffect(() => {
    const routesLookup = new Set(staticRoutes.fixed.concat(staticRoutes.fixed.map((route) => "/" + route)))
    if (contentRef.current == null) return;

    // The links live inside server-provided HTML (dangerouslySetInnerHTML), so they are plain
    // <a> tags rather than React Router <NavLink>s. For links that point to known internal
    // routes we hijack the click and navigate via history.push instead of letting the browser
    // follow the href. A normal <a> navigation would trigger a full page load — re-fetching and
    // re-bootstrapping the whole SPA bundle and discarding in-memory state. history.push keeps
    // navigation client-side, so these embedded links behave like the app's other SPA links
    // (faster, no reload, state preserved). External / unknown links keep default behavior.
    const handlers: Array<{ link: HTMLAnchorElement, handler: (e: MouseEvent) => void }> = [];
    Array.from(contentRef.current.getElementsByTagName("a"))
      .filter(link => {
        const href = link.getAttribute("href") || ""
        return routesLookup.has(href) || staticRoutes.shortUrlTags.some(tag => href.startsWith("/" + tag))
      })
      .forEach(link => {
        const handler = (e: MouseEvent) => {
          history.push(link.getAttribute("href") || "")
          e.preventDefault()
        }
        link.addEventListener("click", handler)
        handlers.push({ link, handler })
      })

    return () => {
      handlers.forEach(({ link, handler }) => link.removeEventListener("click", handler))
    }
  }, [contentRef, history, staticRoutes, innerHtml])

  const content = createElement(tag || "div", {
    ref: contentRef,
    ...tagAttrs
  })

  return content;
}

import { createElement, ReactHTML, useEffect, useRef } from 'react';
import { useHistory } from 'react-router';

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

  useEffect(() => {
    const routesLookup = new Set(staticRoutes.fixed.concat(staticRoutes.fixed.map((route) => "/" + route)))
    if (contentRef.current == null) return;

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
  }, [contentRef, history, staticRoutes])

  const content = createElement(tag || "div", {
    ref: contentRef,
    dangerouslySetInnerHTML: { __html: innerHtml },
    ...tagAttrs
  })

  return content;
}

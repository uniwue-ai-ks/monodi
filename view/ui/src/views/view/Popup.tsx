import { ReactElement, useEffect, useState } from "react"

import './Popup.scss'

export type Props = {
  children: ReactElement[] | ReactElement | null;
  title: string;
  state: State | null;
  setState: (state: State | null) => void;
}

export type State = {
  upperLeft: { x: number, y: number };
  lowerRight: { x: number, y: number };
}

export const getOpenPopupState = () => {
  // get window dimensions
  const width = window.innerWidth;
  const height = window.innerHeight;
  return {
    upperLeft: { x: width * 5 / 10, y: height / 10 },
    lowerRight: { x: width * 9 / 10, y: height * 9 / 10 }
  }
}

type MouseDownState = {
  kind: "title" | "corner";
  mouseDown: { x: number, y: number };
}

export const Popup = ({ title, children, state, setState }: Props) => {
  const [mouseDown, setMouseDown] = useState<null | MouseDownState>(null);
  useEffect(() => {
    if (mouseDown && state) {
      const minWidth = window.innerWidth * 0.2;
      const minHeight = window.innerHeight * 0.2;
      const onMouseMove = (e: MouseEvent) => {
        const upperLeftX = state.upperLeft.x + (e.clientX - mouseDown.mouseDown.x);
        const upperLeftY = state.upperLeft.y + (e.clientY - mouseDown.mouseDown.y);
        const lowerRightX = state.lowerRight.x + (e.clientX - mouseDown.mouseDown.x);
        const lowerRightY = state.lowerRight.y + (e.clientY - mouseDown.mouseDown.y);
        if (mouseDown.kind === "title") {
          setState({
            upperLeft: { x: upperLeftX, y: upperLeftY },
            lowerRight: { x: lowerRightX, y: lowerRightY }
          });
        }

        if (mouseDown.kind === "corner") {
          setState({
            upperLeft: { x: Math.min(state.upperLeft.x, lowerRightX - minWidth), y: Math.min(state.upperLeft.y, lowerRightY - minHeight) },
            lowerRight: { x: lowerRightX, y: lowerRightY }
          });
        }
      }

      const onMouseUp = () => {
        setMouseDown(null);
      }

      window.addEventListener('mousemove', onMouseMove);
      window.addEventListener('mouseup', onMouseUp);

      return () => {
        window.removeEventListener('mousemove', onMouseMove);
        window.removeEventListener('mouseup', onMouseUp);
      }
    }

    // state and setState deliberately left out to not cause loop
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mouseDown]);

  const open = !!state;
  const claz = open ? 'component_popup component_popup--open' : 'component_popup';
  const overlayClaz = open && mouseDown ? 'component_popup__overlay component_popup__overlay--moving' : 'component_popup__overlay';

  const pos =
    state ? {
      top: state.upperLeft.y,
      left: state.upperLeft.x,
      height: state.lowerRight.y - state.upperLeft.y,
      width: state.lowerRight.x - state.upperLeft.x,
    } : {}

  return <div className={claz} style={pos}>
    <div className={overlayClaz}></div>
    <div className="component_popup__header">
      <div className="component_popup__title" onMouseDown={ev => setMouseDown({ kind: "title", mouseDown: { x: ev.clientX, y: ev.clientY } })}>{title}</div>
      <div onClick={() => setState(null)} className="component_popup__close">✖</div>
    </div>
    <div className="component_popup__body">
      {children}
    </div>
    <div className="component_popup__lower-right" onMouseDown={ev => setMouseDown({ kind: "corner", mouseDown: { x: ev.clientX, y: ev.clientY } })}>
      <div className="component_popup__lower-right-inner"></div>
    </div>
  </div>
}

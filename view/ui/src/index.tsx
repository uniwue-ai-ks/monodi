import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter as Router } from 'react-router-dom';
import { getContent } from "./api";
import './index.css';
import * as serviceWorker from './serviceWorker';
import { App } from './views/app/App';
import { library } from '@fortawesome/fontawesome-svg-core'
import { fas } from '@fortawesome/free-solid-svg-icons'

(async () => {

  const [customCss, customJs] = await Promise.all([
    getContent("http://olyro.de/mondiview/customCss"),
    getContent("http://olyro.de/mondiview/customJavascript")
  ]);

  if (customJs) {
    new Function(customJs)();
  }

  // 👇️ IMPORTANT: use correct ID of your root element
  // this is the ID of the div in your index.html file
  const rootElement = document.getElementById('root');
  if (!rootElement) throw new Error('Failed to find the root element');
  const root = createRoot(rootElement);

  const style = document.createElement("style");
  style.innerHTML = customCss!;
  document.getElementsByTagName("head")[0].appendChild(style);

  // 👇️ if you use TypeScript, add non-null (!) assertion operator
  // const root = createRoot(rootElement!);

  library.add(fas)
  const basePath = (window as any).__BASE_PATH__ || "";

  root.render(
    <StrictMode>
      <Router basename={basePath}>
        <App />
      </Router>
    </StrictMode>
  );


  // ReactDOM.render(<App />, document.getElementById('root'));
})();

// If you want your app to work offline and load faster, you can change
// unregister() to register() below. Note this comes with some pitfalls.
// Learn more about service workers: https://bit.ly/CRA-PWA
serviceWorker.unregister();

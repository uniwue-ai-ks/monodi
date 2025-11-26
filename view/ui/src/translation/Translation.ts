import json from "./translation.json";

interface LanguageItem {
  language: string;
  translation: string;
}

export type LangOverrides = { [key: string]: { [lang: string]: string | undefined } | undefined }

export type LangContextType = {
  lang: string;
  overrides: LangOverrides;
};


export class Translation {

  public static getTranslation = (key: string, lang: string, overrides: LangOverrides = {}): string => {
    const override = overrides[key]?.[lang];
    if (override) {
      return override;
    } else {
      const langItem: LanguageItem | undefined = json.items.find(t => t.key === key)?.langItems.find(k => k.language === lang);
      if (langItem) {
        return langItem.translation;
      }
      return "Translation Missing";
    }
  }
}

export default Translation;

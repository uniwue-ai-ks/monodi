import { useEffect, useState } from "react";

export const useDebounce = <A>(value: A, timeout: number = 500): A => {
  const [a, setA] = useState(value);

  useEffect(() => {
    console.log("eff");
    const handlerId = setTimeout(() => {
      setA(value);
      console.log("setting");
    }, timeout);

    return () => clearTimeout(handlerId);
  }, [value, timeout]);

  return a;
}

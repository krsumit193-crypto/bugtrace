import { useEffect, useState } from 'react';

/**
 * Small data-fetching helper shared by every page: loading, error, reload.
 *
 * The `cancelled` flag prevents a slow response from an old filter/page from
 * overwriting newer data — a classic race condition in search UIs.
 *
 * @param {() => Promise<any>} load - async function that fetches the data
 * @param {Array} dependencies - values that should trigger a refetch when changed
 * @returns {{data: any, error: Error|null, loading: boolean, reload: () => void}}
 */
export default function useAsync(load, dependencies = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    // Promise.resolve().then(load) also captures synchronous throws from load(),
    // so a broken caller still ends up in the error state instead of crashing.
    Promise.resolve()
      .then(load)
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setLoading(false);
        }
      })
      .catch((cause) => {
        if (!cancelled) {
          setError(cause);
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
    // The caller owns the dependency list; nonce forces a manual refetch.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...dependencies, nonce]);

  return { data, error, loading, reload: () => setNonce((value) => value + 1) };
}

import { useEffect, useState } from 'react';

/** Small data-fetching helper shared by every page: loading, error, reload. */
export default function useAsync(load, dependencies = []) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    load()
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

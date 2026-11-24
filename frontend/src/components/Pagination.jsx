/**
 * Shared pagination controls. Buttons carry explicit labels because the
 * visible text ("Previous"/"Next") is repeated on several pages, which makes
 * screen-reader navigation ambiguous without them.
 */
export default function Pagination({ page, totalPages, totalElements, onPageChange }) {
  if (totalPages <= 1) {
    return totalElements > 0 ? (
      <p className="pagination-summary" aria-live="polite">{totalElements} result(s)</p>
    ) : null;
  }

  return (
    <div className="pagination">
      <button
        type="button"
        className="button button-ghost"
        aria-label="Previous page"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous
      </button>
      <span className="pagination-summary" aria-live="polite">
        Page {page + 1} of {totalPages} · {totalElements} results
      </span>
      <button
        type="button"
        className="button button-ghost"
        aria-label="Next page"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}

export default function Pagination({ page, totalPages, totalElements, onPageChange }) {
  if (totalPages <= 1) {
    return totalElements > 0 ? (
      <p className="pagination-summary">{totalElements} result(s)</p>
    ) : null;
  }

  return (
    <div className="pagination">
      <button
        type="button"
        className="button button-ghost"
        disabled={page <= 0}
        onClick={() => onPageChange(page - 1)}
      >
        Previous
      </button>
      <span className="pagination-summary">
        Page {page + 1} of {totalPages} · {totalElements} results
      </span>
      <button
        type="button"
        className="button button-ghost"
        disabled={page >= totalPages - 1}
        onClick={() => onPageChange(page + 1)}
      >
        Next
      </button>
    </div>
  );
}

document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll("[data-admin-filter]").forEach((input) => {
        const table = document.querySelector(input.dataset.adminFilter);
        if (!table) return;

        const rows = Array.from(table.querySelectorAll("tbody tr"));
        const count = input.dataset.filterCount
            ? document.querySelector(input.dataset.filterCount)
            : null;
        const update = () => {
            const query = input.value.trim().toLocaleLowerCase();
            let visible = 0;
            rows.forEach((row) => {
                const matches = row.textContent.toLocaleLowerCase().includes(query);
                row.hidden = !matches;
                if (matches) visible += 1;
            });
            if (count) count.textContent = `${visible} of ${rows.length} shown`;
        };
        input.addEventListener("input", update);
        update();
    });

    document.addEventListener("submit", (event) => {
        const message = event.target instanceof HTMLFormElement
            ? event.target.dataset.confirm
            : null;
        if (message && !window.confirm(message)) event.preventDefault();
    });
});

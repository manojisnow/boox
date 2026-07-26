import '@testing-library/jest-dom/vitest';

// jsdom doesn't implement scrollIntoView; ChatBox calls it to auto-scroll to
// the latest message on every render.
if (!window.HTMLElement.prototype.scrollIntoView) {
    window.HTMLElement.prototype.scrollIntoView = () => {};
}

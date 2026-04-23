const navToggle = document.querySelector(".nav-toggle");
const siteNav = document.querySelector(".site-nav");
const navLinks = Array.from(document.querySelectorAll(".site-nav a"));
const sections = Array.from(document.querySelectorAll("main section[id]"));
const revealItems = document.querySelectorAll(".reveal");
const screencastVideo = document.querySelector(".screencast-video");

if (navToggle && siteNav) {
    navToggle.addEventListener("click", () => {
        const isOpen = siteNav.classList.toggle("is-open");
        navToggle.setAttribute("aria-expanded", String(isOpen));
    });
}

navLinks.forEach((link) => {
    link.addEventListener("click", () => {
        siteNav?.classList.remove("is-open");
        navToggle?.setAttribute("aria-expanded", "false");
    });
});

const setActiveNav = () => {
    const scrollY = window.scrollY + 160;
    let currentId = "";

    sections.forEach((section) => {
        if (scrollY >= section.offsetTop) {
            currentId = section.id;
        }
    });

    navLinks.forEach((link) => {
        const href = link.getAttribute("href");
        link.classList.toggle("is-active", href === `#${currentId}`);
    });
};

window.addEventListener("scroll", setActiveNav);
window.addEventListener("load", setActiveNav);

const revealObserver = new IntersectionObserver(
    (entries) => {
        entries.forEach((entry) => {
            if (entry.isIntersecting) {
                entry.target.classList.add("is-visible");
                revealObserver.unobserve(entry.target);
            }
        });
    },
    {
        threshold: 0.16,
    },
);

revealItems.forEach((item) => revealObserver.observe(item));

if (screencastVideo) {
    const applyVideoPosterFromFrame = () => {
        const canvas = document.createElement("canvas");
        canvas.width = screencastVideo.videoWidth;
        canvas.height = screencastVideo.videoHeight;

        if (!canvas.width || !canvas.height) {
            return;
        }

        const context = canvas.getContext("2d");
        if (!context) {
            return;
        }

        context.drawImage(screencastVideo, 0, 0, canvas.width, canvas.height);
        screencastVideo.poster = canvas.toDataURL("image/jpeg", 0.92);
    };

    const seekToPreviewFrame = () => {
        if (screencastVideo.readyState >= 2) {
            screencastVideo.currentTime = 0.1;
        }
    };

    screencastVideo.addEventListener("loadeddata", seekToPreviewFrame, { once: true });
    screencastVideo.addEventListener("seeked", applyVideoPosterFromFrame, { once: true });
}

document.addEventListener("DOMContentLoaded", () => {
    // Header scroll effect
    const mainHeader = document.getElementById('main-header');
    if (mainHeader) {
        window.addEventListener('scroll', () => {
            if (window.scrollY > 10) {
                mainHeader.classList.add('shadow-sm');
                mainHeader.classList.replace('py-4', 'py-3');
            } else {
                mainHeader.classList.remove('shadow-sm');
                mainHeader.classList.replace('py-3', 'py-4');
            }
        });
    }

    document.querySelectorAll('[data-home-carousel]').forEach((carousel) => {
        const track = carousel.querySelector('[data-carousel-track]');
        const previousButton = carousel.closest('[data-home-section]')?.querySelector('[data-carousel-previous]');
        const nextButton = carousel.closest('[data-home-section]')?.querySelector('[data-carousel-next]');

        if (!track || track.children.length < 2) return;

        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        let autoplayTimer;
        let transitionFallback;
        let isPaused = false;
        let isAnimating = false;

        const slideStep = () => {
            const firstSlide = track.querySelector('[data-carousel-slide]');
            if (!firstSlide) return 0;
            const gap = Number.parseFloat(window.getComputedStyle(track).columnGap) || 0;
            return firstSlide.getBoundingClientRect().width + gap;
        };

        const stopAutoplay = () => {
            window.clearInterval(autoplayTimer);
            autoplayTimer = undefined;
        };

        const startAutoplay = () => {
            stopAutoplay();
            if (!isPaused && !reducedMotion.matches) {
                autoplayTimer = window.setInterval(showNext, 5000);
            }
        };

        const showNext = () => {
            if (isAnimating) return;
            const step = slideStep();
            if (!step) return;
            if (reducedMotion.matches) {
                track.append(track.firstElementChild);
                return;
            }
            isAnimating = true;
            track.style.transition = '';
            track.style.transform = `translateX(-${step}px)`;

            let finished = false;
            const finish = () => {
                if (finished) return;
                finished = true;
                isAnimating = false;
                window.clearTimeout(transitionFallback);
                track.removeEventListener('transitionend', onTransitionEnd);
                track.style.transition = 'none';
                track.append(track.firstElementChild);
                track.style.transform = 'translateX(0)';
                track.offsetHeight;
                track.style.transition = '';
            };
            const onTransitionEnd = (event) => {
                if (event.target === track && event.propertyName === 'transform') finish();
            };
            track.addEventListener('transitionend', onTransitionEnd);
            transitionFallback = window.setTimeout(finish, 600);
        };

        const showPrevious = () => {
            if (isAnimating) return;
            const step = slideStep();
            if (!step) return;
            track.style.transition = 'none';
            track.prepend(track.lastElementChild);
            track.style.transform = `translateX(-${step}px)`;
            track.offsetHeight;
            track.style.transition = '';
            track.style.transform = 'translateX(0)';
        };

        nextButton?.addEventListener('click', () => {
            showNext();
            startAutoplay();
        });
        previousButton?.addEventListener('click', () => {
            showPrevious();
            startAutoplay();
        });
        carousel.addEventListener('mouseenter', () => {
            isPaused = true;
            stopAutoplay();
        });
        carousel.addEventListener('mouseleave', () => {
            isPaused = false;
            startAutoplay();
        });
        carousel.addEventListener('focusin', () => {
            isPaused = true;
            stopAutoplay();
        });
        carousel.addEventListener('focusout', (event) => {
            if (!carousel.contains(event.relatedTarget)) {
                isPaused = false;
                startAutoplay();
            }
        });
        reducedMotion.addEventListener('change', startAutoplay);
        startAutoplay();
    });
});

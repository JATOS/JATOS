/*
 * Color mode toggler for Bootstrap's docs (https://getbootstrap.com/)
 * Copyright 2011-2023 The Bootstrap Authors
 * Licensed under the Creative Commons Attribution 3.0 Unported License.
 *
 * Adopted for JATOS (www.jatos.org)
 *
 * !!! Don't turn this into a module!!! It has to be executed as earliest as possible to prevent a white flicker of
 * the screen, but modules are always 'defer'.
 */

(() => {
    'use strict'

    const getStoredTheme = () => localStorage.getItem('theme')
    const setStoredTheme = theme => localStorage.setItem('theme', theme)

    // Other script can listen to this event and toggle their color theme
    const themeChangeEvent = new Event("colorThemeChange");

    // Gets the preferred theme from browser storage or from system's color theme
    const getPreferredTheme = () => {
        const storedTheme = getStoredTheme()
        if (storedTheme) {
            return storedTheme
        }
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
    }

    // Actually applies the color theme to the page
    const setTheme = theme => {
        if (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            document.documentElement.setAttribute('data-bs-theme', 'dark')
        } else {
            document.documentElement.setAttribute('data-bs-theme', theme);
        }
        document.dispatchEvent(themeChangeEvent);
    }

    // Set theme as early as possible, right after script loaded to avoid a white flashing of the screen with dark mode
    // that would happen if only set via the DOMContentLoaded event.
    setTheme(getPreferredTheme());

    // Show which theme is active in the theme picker dropdown
    const showActiveTheme = (theme, focus = false) => {
        const themeSwitcher = document.querySelector('#colorTheme button')

        if (!themeSwitcher) {
            return
        }

        const themeSwitcherText = document.querySelector('#colorThemeText')
        const activeThemeIcon = document.querySelector('.theme-icon-active i')
        const btnToActive = document.querySelector(`[data-bs-theme-value="${theme}"]`)
        const iconOfActiveBtn = btnToActive.querySelector('i:not([class*="check"])').className

        document.querySelectorAll('[data-bs-theme-value]').forEach(element => {
            element.classList.remove('active')
        })

        btnToActive.classList.add('active')
        activeThemeIcon.className = iconOfActiveBtn
        const themeSwitcherLabel = `${themeSwitcherText.textContent} (${btnToActive.dataset.bsThemeValue})`

        if (focus) {
            themeSwitcher.focus()
        }
    }

    // Listen to changes in system's color theme
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        const storedTheme = getStoredTheme()
        if (storedTheme !== 'light' && storedTheme !== 'dark') {
            setTheme(getPreferredTheme())
        }
    })

    // When document is loaded ...
    document.addEventListener('DOMContentLoaded', () => {
        setTheme(getPreferredTheme())
        showActiveTheme(getPreferredTheme())

        // Add click event listener to each theme picker dropdown item
        document.querySelectorAll('[data-bs-theme-value]').forEach(toggle => {
            toggle.addEventListener('click', () => {
                const theme = toggle.getAttribute('data-bs-theme-value')
                setStoredTheme(theme)
                setTheme(theme)
                showActiveTheme(theme, true)
            })
        })
    })
})()
// (string | null) -> string | null
function findMatchingTranslationUrl() {
  const altLinks = document.querySelectorAll('head link[rel="alternate"]');

  for (const browserLangCountry of navigator.languages) {
    const browserLang = browserLangCountry.split('-')[0];

    // If we encounter English in preferences, stop - user prefers English over
    // remaining languages
    if (browserLang === 'en') {
      return null;
    }

    // Check if we have a translation for this non-English language
    const matchingTranslation = Array.from(altLinks).find(link =>
      link.getAttribute('hreflang') === browserLang
    );
    if (matchingTranslation) {
      return matchingTranslation.getAttribute('href');
    }
  }
  return null;
}

// () -> boolean
function attemptLanguageRedirect() {
  // No redirect if we're on a non-English page
  if (document.documentElement.getAttribute('lang') !== 'en') {
    return false;
  }

  // Check if user has previously selected a language
  const storedLanguage = localStorage.getItem('selected_language');
  if (storedLanguage) {
    if (storedLanguage !== 'en') {
      // Find translation for the stored language
      const altLinks = document.querySelectorAll('head link[rel="alternate"]');
      const matchingTranslation = Array.from(altLinks).find(link =>
        link.getAttribute('hreflang') === storedLanguage
      );

      if (matchingTranslation) {
        window.location.href = matchingTranslation.getAttribute('href');
        return true;
      }
      // If stored language is no longer available, clear it
      localStorage.removeItem('selected_language');
    } else {
      // User explicitly selected English, so don't redirect
      return false;
    }
  }

  // Fall back to browser preference if no stored language
  const redirectUrl = findMatchingTranslationUrl();
  if (redirectUrl) {
    window.location.href = redirectUrl;
  }
  return redirectUrl !== null;
}

document.addEventListener('DOMContentLoaded', () => {
  if (attemptLanguageRedirect()) {
    return;
  }

  // If the user clicks a language button, record the selected language
  const languageLinks = document.querySelectorAll('#lang-buttons a[data-lang]');
  languageLinks.forEach(link => {
    link.addEventListener('click', () => {
      const selectedLanguage = link.getAttribute('data-lang');
      localStorage.setItem('selected_language', selectedLanguage);
    });
  });
});

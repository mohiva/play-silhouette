#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import sys, os

on_rtd = os.environ.get('READTHEDOCS', None) == 'True'

# -- General configuration ------------------------------------------------

extensions = ['sphinx.ext.ifconfig', 'sphinx.ext.extlinks']
templates_path = ['_templates']
exclude_patterns = ['_build']
source_suffix = '.rst'
master_doc = 'index'
project = 'Silhouette'
copyright = '2015, Christian Kaps'
last_stable = '1.0'
version = '2'
release = '2.0-SNAPSHOT'
pygments_style = 'sphinx'
rst_prolog = '''
.. |last_stable| replace:: :silhouette-doc:`{0}`
'''.format(last_stable, release)

extlinks = {
    'silhouette-doc':  ('http://docs.silhouette.mohiva.com/%s/', ''),
    'silhouette-api-doc': ('http://silhouette.mohiva.com/api/%s/', ''),
    'silhouette-htmlzip-doc': ('https://readthedocs.org/projects/silhouette/downloads/htmlzip/%s/', ''),
    'silhouette-pdf-doc': ('https://readthedocs.org/projects/silhouette/downloads/pdf/%s/', ''),
    'silhouette-epub-doc': ('https://readthedocs.org/projects/silhouette/downloads/epub/%s/', '')
}

# -- Options for HTML output ----------------------------------------------

html_theme = 'default'
if not on_rtd:
    try:
        import sphinx_rtd_theme
        html_theme = 'sphinx_rtd_theme'
        html_theme_path = [sphinx_rtd_theme.get_html_theme_path()]
    except ImportError:
        pass

html_static_path = ['_static']

# If true, SmartyPants will be used to convert quotes and dashes to
# typographically correct entities.
html_use_smartypants = True

# Output file base name for HTML help builder.
htmlhelp_basename = 'Silhouettedoc'

def setup(app):
    # overrides for wide tables in RTD theme
    app.add_stylesheet('theme_overrides.css')   # path relative to _static


# -- Options for LaTeX output ---------------------------------------------

latex_elements = {
# The paper size ('letterpaper' or 'a4paper').
#'papersize': 'letterpaper',

# The font size ('10pt', '11pt' or '12pt').
#'pointsize': '10pt',

# Additional stuff for the LaTeX preamble.
#'preamble': '',
}

# Grouping the document tree into LaTeX files. List of tuples
# (source start file, target name, title,
#  author, documentclass [howto, manual, or own class]).
latex_documents = [
  ('index', 'Silhouette.tex', 'Silhouette Documentation',
   'Christian Kaps', 'manual'),
]

# -- Options for manual page output ---------------------------------------

# One entry per manual page. List of tuples
# (source start file, name, description, authors, manual section).
man_pages = [
    ('index', 'silhouette', 'Silhouette Documentation',
     ['Christian Kaps'], 1)
]

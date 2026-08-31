%% msereport.cls
%% ---------------------------------------------------------------------------
%% 1:1-Nachbau der Word-Vorlage "template.docx" (Mobile Software Engineering,
%% Prof. Dr. Marc Hesenius, HAWK / EnIA) als LaTeX-Dokumentklasse.
%%
%% Alle Masse sind direkt aus dem OOXML der Vorlage uebernommen:
%%   1 twip = 1/1440 in.  Word-Halbpunkte (w:sz) = pt*2.
%%   Zeilenabstand "einfach" bei Arial entspricht 1.15 * Schriftgroesse.
%% ---------------------------------------------------------------------------
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{msereport}[2026/08/25 v1.0 MSE Project Report - Nachbau template.docx]

\LoadClass[11pt,a4paper]{article}

% --- Schrift: Arial (Word docDefaults: rFonts ascii="Arial") -----------------
\RequirePackage{iftex}
\ifPDFTeX
  \RequirePackage[T1]{fontenc}
  \RequirePackage[utf8]{inputenc}
  \RequirePackage{textcomp}
  \RequirePackage[scaled=1.0]{helvet}   % Nimbus Sans - metrisch wie Arial
\else
  \RequirePackage{fontspec}
  \IfFontExistsTF{Arial}%
    {\setsansfont{Arial}}%
    {\IfFontExistsTF{Liberation Sans}{\setsansfont{Liberation Sans}}{\setsansfont{Helvetica}}}
\fi
\renewcommand{\familydefault}{\sfdefault}

\RequirePackage{xcolor}
\RequirePackage{graphicx}
\RequirePackage{array}
\RequirePackage{tabularray}
\UseTblrLibrary{varwidth}
\RequirePackage{enumitem}
\RequirePackage{titlesec}
% nocheck: fancyhdr verlangt sonst einen Sicherheitsaufschlag von ~1 Zeile auf
% \headheight/\footskip.  Kopf und Fuss sind hier auf 0,1 pt genau vermessen,
% der Aufschlag wuerde den Satzspiegel nur unnoetig verkleinern.
\RequirePackage[nocheck]{fancyhdr}
\RequirePackage{calc}
\RequirePackage[hidelinks,pdfusetitle]{hyperref}

% --- Farben (Word: w:color / w:fill) ----------------------------------------
\definecolor{msered}{HTML}{B31B1B}      % Ueberschriften, Titel, Header-Linie
\definecolor{msered2}{HTML}{B31A1B}     % Footer-Linie, Tabellenkopf (Var. A)
\definecolor{msetablered}{HTML}{C00000} % Tabellenkopf (Var. B)
\definecolor{msetext}{HTML}{444444}     % Fliesstext
\definecolor{msegray}{HTML}{777777}     % Kopf-/Fusszeilen-Sekundaertext
\definecolor{mseborder}{HTML}{BFBFBF}   % Tabellenlinien

% --- Schriftgroessen (Word w:sz / 2) ----------------------------------------
\newcommand{\msebodyfont}{\fontsize{11pt}{12.65pt}\selectfont}   % w:sz 22
\newcommand{\msenotefont}{\fontsize{10.5pt}{12.08pt}\selectfont} % w:sz 21
\newcommand{\msesmallfont}{\fontsize{10pt}{11.5pt}\selectfont}   % w:sz 20
\newcommand{\msesubfont}{\fontsize{9pt}{10.35pt}\selectfont}     % w:sz 18
\newcommand{\msefootfont}{\fontsize{8pt}{9.2pt}\selectfont}      % w:sz 16
\newcommand{\mseleadfont}{\fontsize{12pt}{13.8pt}\selectfont}    % w:sz 24
\newcommand{\msehtwofont}{\fontsize{13pt}{14.95pt}\selectfont}   % w:sz 26
\newcommand{\msehonefont}{\fontsize{18pt}{20.7pt}\selectfont}    % w:sz 36

% --- Seitenlayout -----------------------------------------------------------
% w:pgSz 11906x16838 (A4) | w:pgMar top/bottom 998, left/right 1134,
% header 709, footer 709 (twips).  Der Kopf ist hoeher als der obere Rand,
% also beginnen Textkoerper - wie in Word - direkt unter dem Kopf bzw. direkt
% ueber dem Fuss:  Kopflinie bei 3,45 cm, Satzspiegel 3,51 cm bis 27,36 cm,
% Unterkante der Fusszeile bei 28,45 cm.
% \headheight/\footskip sind exakt die gemessenen Hoehen von \mseheadercontent
% bzw. \msefootercontent.
\RequirePackage[
  a4paper,
  left=2cm, right=2cm,
  top=1.25cm, includehead, headheight=64.1pt, headsep=0pt,
  % w:footer misst vom unteren Seitenrand bis zur UNTERKANTE der Fusszeile.
  bottom=1.25cm, includefoot, footskip=31pt
]{geometry}

% --- Absatzverhalten wie in Word --------------------------------------------
\setlength{\parindent}{0pt}
\setlength{\parskip}{0pt}
\setlength{\emergencystretch}{3em}
\AtBeginDocument{%
  \color{msetext}%
  \msebodyfont
  \raggedright
  \hyphenpenalty=10000
  \exhyphenpenalty=10000
}

% --- Kopfzeile --------------------------------------------------------------
% header1.xml: Tabelle 2835 + 6191 twips (5,00 cm + 10,92 cm), Zeilenhoehe
% 1134 twips (2,00 cm), links das EnIA-Logo (1270635 x 526415 EMU =
% 3,53 x 1,46 cm), rechts zwei rechtsbuendige Zeilen.  Darunter ein Absatz
% mit unterer Rahmenlinie w:sz 12 (= 1,5 pt) in B31B1B ueber die volle
% Textbreite, Abstand davor 60 twips (= 3 pt).
\newcommand{\mseheadercontent}{%
  \begin{minipage}[c][2cm][c]{5.001cm}%
    \includegraphics[width=3.53cm]{assets/enia-logo.png}%
  \end{minipage}%
  \begin{minipage}[c][2cm][c]{10.919cm}%
    \raggedleft
    {\bfseries\color{msered}\mseleadfont Mobile Software Engineering}\par
    {\color{msegray}\msesubfont Prof.\ Dr.\ Marc Hesenius}\par
  \end{minipage}%
  \par\vspace{3pt}%
  \rule{0pt}{1.15pt}%      1-pt-Absatz (w:sz 2), traegt die Rahmenlinie
  \par\nointerlineskip
  {\color{msered}\rule{\textwidth}{1.5pt}}%
}

% --- Fusszeile --------------------------------------------------------------
% footer1.xml: Absatz mit oberer Rahmenlinie w:sz 6 (= 0,75 pt) in B31A1B,
% w:space 1 (= 1 pt Abstand), Schrift w:sz 10 (= 5 pt).  Danach Tabelle
% 2268 + 5102 + 2268 twips (4,00 + 9,00 + 4,00 cm = volle Textbreite),
% Text 8 pt in 777777, links / zentriert / rechts.
\newcommand{\msefootercontent}{%
  {\color{msered2}\rule{\textwidth}{0.75pt}}%
  \par\vspace{1pt}%
  \rule{0pt}{5.75pt}\par
  \color{msegray}\msefootfont
  \begin{minipage}[t]{4.001cm}\raggedright Mobile Software Engineering\end{minipage}%
  \begin{minipage}[t]{9.000cm}\centering Engineering Interactive Applications -- University of Hildesheim\end{minipage}%
  \begin{minipage}[t]{4.001cm}\raggedleft Page \thepage\end{minipage}%
  \par\rule{0pt}{5.75pt}%
}

\fancypagestyle{msereport}{%
  \fancyhf{}%
  \renewcommand{\headrulewidth}{0pt}%
  \renewcommand{\footrulewidth}{0pt}%
  \fancyhead[L]{\parbox[b]{\textwidth}{\mseheadercontent}}%
  \fancyfoot[L]{\parbox[b]{\textwidth}{\msefootercontent}}%
}
\pagestyle{msereport}

% --- Ueberschriften ---------------------------------------------------------
% Heading1 (styles.xml): basedOn Heading2, w:sz 36 (18 pt), fett, B31B1B,
%   spacing before 360 (18 pt) / after 240 (12 pt), untere Rahmenlinie
%   w:sz 6 (0,75 pt) B31B1B mit w:space 4 (4 pt), Nummer "%1."
% Heading2: w:sz 26 (13 pt), fett, B31B1B, spacing before 240 (12 pt) /
%   after 80 (4 pt), gleiche Rahmenlinie, Nummer "%1.%2."
% Die Tabulatorposition nach der Nummer ist der Word-Standardtabulator
% (defaultTabStop 720 twips = 1,27 cm).
\newlength{\msenumwidth}
\setlength{\msenumwidth}{1.27cm}

\titleformat{\section}[block]
  {\bfseries\color{msered}\msehonefont\raggedright}
  {\makebox[\msenumwidth][l]{\thesection.}}{0pt}{}
  [{\vspace{4pt}\color{msered}\titlerule[0.75pt]}]
\titlespacing*{\section}{0pt}{18pt}{12pt}

\titleformat{\subsection}[block]
  {\bfseries\color{msered}\msehtwofont\raggedright}
  {\makebox[\msenumwidth][l]{\thesubsection.}}{0pt}{}
  [{\vspace{4pt}\color{msered}\titlerule[0.75pt]}]
\titlespacing*{\subsection}{0pt}{12pt}{4pt}

% Heading3 (in der Vorlage definiert, aber ungenutzt): 12 pt fett, ohne Linie
\titleformat{\subsubsection}[block]
  {\bfseries\color{msetext}\fontsize{12pt}{13.8pt}\selectfont\raggedright}{\thesubsubsection.}{0.5em}{}
\titlespacing*{\subsubsection}{0pt}{9pt}{3pt}

\setcounter{secnumdepth}{3}

% --- Aufzaehlungen ----------------------------------------------------------
% ListParagraph + numId 30: Bullet, w:ind left 720 (1,27 cm),
% hanging 360 (0,635 cm), keine Absatzabstaende.
\setlist[itemize]{
  label=\textbullet,
  leftmargin=1.27cm, labelindent=0.635cm, labelwidth=0.635cm, labelsep=0pt,
  align=left, topsep=0pt, partopsep=0pt, itemsep=0pt, parsep=0pt
}
\setlist[itemize,2]{leftmargin=1.27cm, labelindent=0.635cm, labelwidth=0.635cm, labelsep=0pt}

% --- Absatz-Bausteine -------------------------------------------------------
% \msehint  - kursiver Hinweistext 10,5 pt (Word: i + sz 21)
% \msehintb - fetter Hinweistext 10,5 pt
% \msegap   - Leerabsatz 11 pt mit 6 pt Abstand davor und danach
%             (Word: spacing before 120 / after 120)
\newcommand{\msehint}[1]{{\itshape\msenotefont #1}\par}
\newcommand{\msehintb}[1]{{\bfseries\msenotefont #1}\par}
\newcommand{\msevspace}[1]{\par\vspace{#1}}
\newcommand{\mseblank}[1][11pt]{\par\rule{0pt}{#1}\par}
\newcommand{\msegap}{\par\vspace{6pt}\rule{0pt}{12.65pt}\par\vspace{6pt}}

% --- Tabellen ---------------------------------------------------------------
% Alle Tabellen: Breite 9360 twips (16,51 cm), Rahmen w:sz 1 (0,125 pt) in
% BFBFBF, Zellrand oben/unten 80 twips (4 pt), links/rechts 120 twips (6 pt).
% Kopfzeile: fett, weiss, 10 pt, roter Hintergrund.
\newcommand{\msetableheadfont}{\bfseries\color{white}\msesmallfont}
\newcommand{\msecellfont}{\color{msetext}\msebodyfont}
% Strut, damit leere Zellen die Word-Zeilenhoehe behalten
\newcommand{\msestrut}{\rule[-3.15pt]{0pt}{12.65pt}}

% Word-Tabellen haben keine Beschriftung und keinen Abstand nach oben/unten,
% duerfen aber ueber Seiten umbrechen (Kopfzeile wird wiederholt: w:tblHeader).
\DefTblrTemplate{caption}{msenocap}{}
\DefTblrTemplate{capcont}{msenocap}{}
\NewTblrTheme{msenocap}{%
  \SetTblrTemplate{caption}{msenocap}%
  \SetTblrTemplate{capcont}{msenocap}%
  \SetTblrTemplate{contfoot}{empty}%   kein "Continued on next page"
  \SetTblrTemplate{conthead}{empty}%   kein "(Continued)"
}

\NewTblrEnviron{msetblr}
\SetTblrInner[msetblr]{
  hlines={0.125pt, mseborder},
  vlines={0.125pt, mseborder},
  colsep=6pt,
  rowsep=4pt,
  stretch=0,
  % Einzige bewusste Abweichung von der Vorlage: die Kopfzeile wird nach
  % einem Seitenumbruch wiederholt (in der .docx ist w:tblHeader nur bei der
  % Team-Tabelle gesetzt).  Verhindert eine allein stehende Kopfzeile am
  % Seitenende.  Mit "rowhead=0" pro Tabelle abschaltbar.
  rowhead=1,
  row{1}={fg=white, font=\bfseries\msesmallfont, valign=m},
  cells={font=\msebodyfont, halign=l, valign=t},
}
\SetTblrOuter[msetblr]{theme=msenocap, long, presep=0pt, postsep=0pt, halign=l}

% --- Titelblock -------------------------------------------------------------
% Absatz zentriert, spacing after 60 (3 pt), fett B31B1B 18 pt;
% Untertitel zusaetzlich kursiv, getrennt durch einen Zeilenumbruch.
\newcommand{\msetitleblock}[2]{%
  \begin{center}
    \rule{0pt}{20.7pt}\par\vspace{3pt}%       leerer 18-pt-Absatz der Vorlage
    {\bfseries\color{msered}\msehonefont #1\\ \itshape #2\par}%
  \end{center}
  \vspace{3pt}%
  \rule{0pt}{13.8pt}\par\vspace{10pt}%        leerer 12-pt-Absatz (after 200)
}

\endinput

#!/bin/bash
DIR="$( cd "$( dirname "$0" )" && pwd )"
texexec --pdfcopy --nobanner --result=$DIR/final.pdf $DIR/portada.pdf $DIR/proyecto.pdf $DIR/anteproxecto/anteproyecto-v1.pdf

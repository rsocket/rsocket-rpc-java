find . -name "*.project" -type f -delete
find . -name "*.settings" -type d -print0 | xargs -0 /bin/rm -rf
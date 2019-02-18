#!/usr/bin/env bash

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then

    echo -e "Building PR #$TRAVIS_PULL_REQUEST [$TRAVIS_PULL_REQUEST_SLUG/$TRAVIS_PULL_REQUEST_BRANCH => $TRAVIS_REPO_SLUG/$TRAVIS_BRANCH]"
    ./gradlew \
        -PvcProtobufLibs="/c/Program Files/protobuf/lib" -PvcProtobufInclude="/c/Program Files/protobuf/include" \
        build

elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ] && [ "$bintrayUser" != "" ] ; then

    echo -e "Building Snapshot $TRAVIS_REPO_SLUG/$TRAVIS_BRANCH"
    ./gradlew \
        -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" \
        -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" \
        -PvcProtobufLibs="/c/Program Files/protobuf/lib" -PvcProtobufInclude="/c/Program Files/protobuf/include" \
        build artifactoryPublish --stacktrace

elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ] && [ "$bintrayUser" != "" ] ; then

    echo -e "Building Tag $TRAVIS_REPO_SLUG/$TRAVIS_TAG"
    ./gradlew \
        -Pversion="$TRAVIS_TAG" -Pstage="$TRAVIS_BUILD_STAGE_NAME" \
        -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" \
        -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" \
        -PvcProtobufLibs="/c/Program Files/protobuf/lib" -PvcProtobufInclude="/c/Program Files/protobuf/include" \
        build bintrayUpload --stacktrace

else

    echo -e "Building $TRAVIS_REPO_SLUG/$TRAVIS_BRANCH"
    ./gradlew \
        -PvcProtobufLibs="/c/Program Files/protobuf/lib" -PvcProtobufInclude="/c/Program Files/protobuf/include" \
        build

fi


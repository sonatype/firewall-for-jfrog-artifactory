<!--

    Copyright 2019, Sonatype, Inc.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

-->
# How to be a contributor to this project

## Talk to a human first

### For Sonatypers

Looking to dive in? FANTASTIC! Reach out to one of our experts and we can help get you started in the right direction.

* Please fill out an issue for your PR so that we have traceability as to what you are trying to fix,
  versus how you fixed it.

### For Non-Sonatypers

* Come hang out with us at our [gitter channel](https://gitter.im/sonatype/nexus-developers) so we can try to understand the problem you are trying to solve.
* Sign the [Sonatype CLA](https://sonatypecla.herokuapp.com/sign-cla)

## Submitting a PR

* Add the @sonatype/iq as a reviewer to the PR
* Try to fix one thing per pull request! Many people work on this code, so the more focused your changes are, the less
  of a headache other people will have when they merge their work in.
* Ensure your Pull Request passes tests

### PR commenting protocol

Here are the general rules to follow when commenting on PRs for this repo:

* When leaving comments on a PR, leave them all as individual comments as opposed to "Starting a Review".  This is
  because some people prefer to receive each comment as a separate email.
* When responding to a comment, always blockquote what you are responding to (even if it is the entire previous
  comment).  This allows the emails to have the necessary conversation context that they otherwise lack
* When you as the PR author make a change in response to a comment, respond to that comment and include the commit hash
  where you made the fix.  Do not resolve the thread
* The originator of a thread should be the person to mark that thread resolved, typically after reviewing the commit
  referenced in the response comment from the PR author and finding it acceptable.
* Unless stated otherwise by the commenter, or clearly not meant to be responded to, all comments on a PR are expected
  to be addressed before it is merged. Sometimes it is alright for a reviewer to approve the PR before all of their
  comments are addressed, but generally only when those comments are expected to be easily addressable without further
  discussion (for example simple formatting issues).  Even in this case though, the comments should still be addressed
  post-approval

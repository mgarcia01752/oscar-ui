$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Commit = 'f7050e591e2f170cb00df7fdd501b2a88cc79860'
$Checkout = "$Root/.tools/oscar-$Commit"
if (!(Test-Path $Checkout)) {
    & git clone https://github.com/Comcast/Oscar.git $Checkout
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & git -C $Checkout checkout --detach $Commit
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
$Head = & git -C $Checkout rev-parse HEAD
if ($LASTEXITCODE -ne 0 -or $Head -ne $Commit) { throw 'Oscar checkout has the wrong commit' }
$Changes = & git -C $Checkout status --porcelain --untracked-files=no
if ($LASTEXITCODE -ne 0 -or $Changes) { throw 'Oscar checkout has tracked changes' }
& "$Root/mvnw.cmd" -B -f "$Checkout/pom.xml" "-Dmaven.repo.local=$Root/.m2" -DskipTests install
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

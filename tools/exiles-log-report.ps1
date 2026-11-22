# Exiles log report -- turns starsector.log into a readable Markdown test-run report.
# Plan: docs/EXILES_SWEEP_AND_TOOLING_PLAN.md Part 2. READ-ONLY: never modifies the log.
#
# Usage:
#   & "D:/Fractal Softworks/Starsector_098.5a/mods/Exiles/tools/exiles-log-report.ps1" [-Log <path>] [-Run campaign|session|all|<n>] [-Out <file.md>] [-NoTimeline]
#
#   Default paths are derived from this script's own location (2026-11-12): the log and the
#   logs-archive folder of the install this copy of the mod sits in (<install>/mods/Exiles/tools).
#
#   -Run campaign  (default) from the last new game ("onNewGameAfterEconomyLoad reached") to the end,
#                  across reloads. If the live log has no new game, the rotated .1 is joined in front.
#   -Run session   the last launch only.   -Run all   the whole file.   -Run <n>   launch number n.
#   -Out           default: <install>\logs-archive\report_<yyyy-MM-dd_HHmm>.md
#
# Dates and the player's system come ONLY from SALVAGE_DIAG lines (carried forward); without them
# they print as "?". Every pattern lives in the tables below: a new log line = a new row.
param(
    [string]$Log = (Join-Path $PSScriptRoot '..\..\..\starsector-core\starsector.log'),
    [string]$Run = "campaign",
    [string]$Out = "",
    [switch]$NoTimeline
)

$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------------------------
# PATTERN TABLES
# ---------------------------------------------------------------------------------------------
# Op lifecycle rows. Ev: launch | arrive | event | outcome. Named groups the engine understands:
#   sys  (target system), tgt (target market name), node (Omicron node -- pairing key),
#   op   (overrides Op), src, fp, def, gate, str, actor, victim, pl (IN / out of spawn range).
# Pairing: an arrive/event/outcome attaches to the OLDEST open op of the same Dir+Op whose sys,
# tgt or node matches; Op '*' matches any op of the Dir; Any=$true takes the oldest open op of
# that Dir+Op regardless of place. A line with no open match attaches to the op last closed in
# the same place, or becomes an orphan row ("launch not in range").
$OpRows = @(
    # --- Heg-League ---
    @{ Dir='Heg-League'; Op='interdiction'; Ev='launch';  Re='Exiles Heg-League INTERDICTION LAUNCHED: (?<src>.+?) -> (?<sys>.+?) star system \| actor=(?<actor>\w+) victim=(?<victim>\w+) \| FP=(?<fp>\d+) defense=(?<def>\d+) gate=(?<gate>\d+)' }
    @{ Dir='Heg-League'; Op='strike';       Ev='launch';  Re='Exiles Heg-League SURGICAL STRIKE LAUNCHED: (?<src>.+?) -> (?<tgt>.+?) in (?<sys>.+?) star system \| actor=(?<actor>\w+) struck=(?<victim>\w+) \| FP=(?<fp>\d+) defense=(?<def>\d+).*?gate=(?<gate>\d+)' }
    @{ Dir='Heg-League'; Op='squeeze';      Ev='launch';  Re='Exiles Heg-League SQUEEZE LAUNCHED: (?<src>.+?) -> (?<tgt>.+?) in (?<sys>.+?) star system \| actor=(?<actor>\w+) victim=(?<victim>\w+) \| FP=(?<fp>\d+) defense=(?<def>\d+).*?gate=(?<gate>\d+)' }
    @{ Dir='Heg-League'; Op='blockade';     Ev='launch';  Re='Exiles Heg-League BLOCKADE LAUNCHED: (?<src>.+?) -> (?<sys>.+?) star system \| actor=(?<actor>\w+) victim=(?<victim>\w+) \| fleetSizes=\[[^\]]*\] mult=\S+ estimate=(?<fp>\d+) defense=(?<def>\d+) gate=(?<gate>\d+)' }
    @{ Dir='Heg-League'; Op='interdiction'; Ev='arrive';  Re='Exiles Heg-League (?<op>interdiction|squeeze) ARRIVED at (?<sys>[^:]+): group strength (?<str>\d+) vs defense (?<def>\d+)' }
    @{ Dir='Heg-League'; Op='strike';       Ev='arrive';  Re='Exiles Heg-League strike ARRIVED at (?<sys>.+?) -- (?<tgt>.+?): group strength (?<str>\d+) vs defense (?<def>\d+)' }
    @{ Dir='Heg-League'; Op='strike';       Ev='event';   Re='Exiles Heg-League strike WINDOW END at (?<sys>.+?) -- (?<tgt>.+?): group strength (?<str>\d+) vs defense (?<def>\d+).*?\| player (?<pl>IN|out of) spawn range' }
    @{ Dir='Heg-League'; Op='strike';       Ev='event';   Re='Exiles Heg-League SURGICAL STRIKE LANDED: (?<tgt>.+?) \(' }
    @{ Dir='Heg-League'; Op='*';            Ev='event';   Re='Exiles Heg-League op ABORTED in stage \d+ \((?<stage>\w+)\).*?\| target (?<sys>[^|]+?) \|' }
    @{ Dir='Heg-League'; Op='interdiction'; Ev='outcome'; Re='Exiles Heg-League interdiction: raiders withdrew from (?<sys>.+?) \((?<st>\w+)\)' }
    @{ Dir='Heg-League'; Op='interdiction'; Ev='outcome'; Re='Exiles Heg-League interdiction ended WITHOUT completing its presence in (?<sys>.+?) --' }
    @{ Dir='Heg-League'; Op='squeeze';      Ev='event';   Re='Exiles Heg-League squeeze: cordon withdrew from (?<sys>.+?) \((?<st>\w+)\)' }
    @{ Dir='Heg-League'; Op='squeeze';      Ev='outcome'; Re='Exiles Heg-League squeeze outcome in (?<sys>.+?) -- anchor (?<tgtid>\S+): ' }
    @{ Dir='Heg-League'; Op='strike';       Ev='outcome'; Re='Exiles Heg-League strike outcome in (?<sys>.+?) -- (?<tgtid>\S+): ' }
    @{ Dir='Heg-League'; Op='blockade';     Ev='outcome'; Re='Exiles Heg-League blockade GATES RELEASED (?<sys>.+?) star system: ' }
    @{ Dir='Heg-League'; Op='blockade';     Ev='outcome'; Re='Exiles Heg-League blockade ended WITHOUT holding its window in (?<sys>.+?) --' }
    @{ Dir='Heg-League'; Op='squeeze';      Ev='event';   Re='Exiles Heg-League VICTORY BONUS: planetary blockade in (?<sys>.+?) star system' }
    @{ Dir='Heg-League'; Op='blockade';     Ev='event';   Re='Exiles Heg-League VICTORY BONUS: blockade in (?<sys>.+?) star system' }
    @{ Dir='Heg-League'; Op='*';            Ev='retleg';  Re='Exiles Heg-League RETURN LEG: \w+ in (?<sys>.+?) \(' }
    # --- Midan (Pass 7 step 7d-2: the Strike Force; one strike in flight at a time) ---
    # Sneak attack (2026-10-30): "nominal source" replaced the 7d-2 "from X (assembling ...)" form.
    @{ Dir='Midan'; Op='strike'; Ev='launch';  Re='Exiles Midan LAUNCHED: rung \d+ (?<tgt>.+?) in (?<sys>.+?) star system \| (?:nominal source (?<src>.+?), home|from (?<src>.+?) \().*?\| FP (?<fp>\d+) \| defense (?<def>\d+)' }
    @{ Dir='Midan'; Op='strike'; Ev='event';   Re='Exiles Midan DISTRESS CALL: (?<tgt>.+?) \('; Any=$true }
    @{ Dir='Midan'; Op='strike'; Ev='event';   Re='Exiles Midan STAND DOWN'; Any=$true }
    @{ Dir='Midan'; Op='strike'; Ev='arrive';  Re='Exiles Midan strike ARRIVED at (?<sys>.+?) -- (?<tgt>.+?): group strength (?<str>\d+) vs defense (?<def>\d+)' }
    @{ Dir='Midan'; Op='strike'; Ev='event';   Re='Exiles Midan strike WINDOW END at (?<sys>.+?) -- (?<tgt>.+?): group strength (?<str>\d+) vs defense (?<def>\d+).*?\| player (?<pl>IN|out of) spawn range' }
    @{ Dir='Midan'; Op='strike'; Ev='event';   Re='Exiles Midan STRIKE LANDED: rung \d+ (?<tgt>.+?) -- ' }
    @{ Dir='Midan'; Op='strike'; Ev='event';   Re='Exiles Midan AJAX LOST: '; Any=$true }
    @{ Dir='Midan'; Op='strike'; Ev='outcome'; Re='Exiles Midan STRIKE OUTCOME: rung \d+ (?<tgtid>\S+) -- '; Any=$true }
    @{ Dir='Midan'; Op='strike'; Ev='event';   Re='Exiles Midan op ABORTED in stage \d+ \((?<stage>\w+)\).*?\| target (?<sys>[^|]+?) \|' }
    @{ Dir='Midan'; Op='*';      Ev='retleg';  Re='Exiles Midan RETURN LEG: \w+ in (?<sys>.+?) \(' }
    # --- Ashen Crusade ---
    @{ Dir='Ashen'; Op='interdiction'; Ev='launch';  Re='Ashen interdiction raid launched: (?<src>.+?) -> (?<sys>.+?) star system, FP=(?<fp>\d+)' }
    @{ Dir='Ashen'; Op='interdiction'; Ev='outcome'; Re='Ashen interdiction raid: raiders withdrew from (?<sys>.+?) \((?<st>\w+)\)' }
    @{ Dir='Ashen'; Op='rehearsal';    Ev='launch';  Re='Ashen rehearsal launched: (?<src>.+?) -> (?<tgt>.+?) in (?<sys>.+?) star system, FP=(?<fp>\d+)' }
    @{ Dir='Ashen'; Op='rehearsal';    Ev='arrive';  Re='Ashen rehearsal ARRIVED at (?<sys>.+?) -- (?<tgt>.+?): group strength (?<str>\d+) vs defense (?<def>\d+)' }
    @{ Dir='Ashen'; Op='rehearsal';    Ev='event';   Re='Ashen AshenRehearsalStage WINDOW END at (?<sys>.+?): strength (?<str>\d+) vs enemy (?<def>\d+).*?\| player (?<pl>IN|out of) spawn range' }
    @{ Dir='Ashen'; Op='rehearsal';    Ev='event';   Re='Ashen rehearsal: struck (?<tgt>.+?) \(' }
    @{ Dir='Ashen'; Op='culann';       Ev='outcome'; Re='Ashen rehearsal ended -- culann: '; Any=$true }
    @{ Dir='Ashen'; Op='rehearsal';    Ev='outcome'; Re='Ashen rehearsal ended -- (?<tgtid>\w+): '; Any=$true }
    @{ Dir='Ashen'; Op='interdiction'; Ev='launch';  Re='ASHEN CULANN STRIKE wave 1 \(interdiction sweep\) launched: (?<fp>\d+) FP in \d+ fleet\(s\) into (?<sys>.+)$' }
    @{ Dir='Ashen'; Op='culann';       Ev='launch';  Re='ASHEN CULANN STRIKE wave 3 \(armadas\) launched against (?<tgt>[^:]+): assault (?<fp>\d+) FP' }
    @{ Dir='Ashen'; Op='culann';       Ev='arrive';  Re='Ashen Culann strike ARRIVED at (?<sys>.+?) -- (?<tgt>.+?): group strength (?<str>\d+) vs defense (?<def>\d+)'; Any=$true }
    @{ Dir='Ashen'; Op='culann';       Ev='event';   Re='Ashen AshenCulannStrikeStage WINDOW END at (?<sys>.+?): strength (?<str>\d+) vs enemy (?<def>\d+).*?\| player (?<pl>IN|out of) spawn range'; Any=$true }
    @{ Dir='Ashen'; Op='culann';       Ev='event';   Re='ASHEN CULANN STRIKE SUCCEEDED at '; Any=$true }
    @{ Dir='Ashen'; Op='*';            Ev='retleg';  Re='Exiles Ashen RETURN LEG: \w+ in (?<sys>.+?) \(' }
    # --- Black Fleet ---
    @{ Dir='Black Fleet'; Op='commerce'; Ev='launch';  Re='Black Fleet commerce raid launched: (?<src>.+?) -> (?<sys>.+?) star system, FP=(?<fp>\d+)' }
    @{ Dir='Black Fleet'; Op='commerce'; Ev='outcome'; Re='Black Fleet commerce raid: corsairs withdrew from (?<sys>.+?) \((?<st>\w+)\)' }
    @{ Dir='Black Fleet'; Op='assault';  Ev='launch';  Re='Black Fleet ASSAULT launched: (?<src>.+?) -> (?<sys>.+?) star system, FP=(?<fp>\d+)' }
    @{ Dir='Black Fleet'; Op='assault';  Ev='event';   Re='Black Fleet assault: sacked (?<tgt>.+?) \('; Any=$true }
    @{ Dir='Black Fleet'; Op='assault';  Ev='event';   Re='Black Fleet assault WINDOW END at (?<sys>.+?): strength (?<str>\d+) vs enemy (?<def>\d+).*?\| player (?<pl>IN|out of) spawn range' }
    @{ Dir='Black Fleet'; Op='assault';  Ev='outcome'; Re='Black Fleet assault: force withdrew from (?<sys>.+?) \((?<st>\w+)\)' }
    @{ Dir='Black Fleet'; Op='*';        Ev='retleg';  Re='Exiles Black Fleet RETURN LEG: \w+ in (?<sys>.+?) \(' }
    # --- Omicron (one op per node at a time: the node is the pairing key) ---
    @{ Dir='Omicron'; Op='raid';      Ev='launch';  Re='Omicron (?<op>raid|INCURSION) op \(node (?<node>[^)]+)\): (?<fp>\d+) FP -> (?<sys>.+?) \(target market (?<tgt>[^,]+),' }
    @{ Dir='Omicron'; Op='raid';      Ev='outcome'; Re='Omicron (?<op>raid|INCURSION) op ENDED: node=(?<node>[^,]+), durationDays' }
    @{ Dir='Omicron'; Op='*';         Ev='retleg';  Re='Exiles Omicron RETURN LEG: \w+ in (?<sys>.+?) \(' }
)

# Watch-list rows: every match is listed (date, player system, line) under its section.
$WatchRows = @(
    @{ Sec='Victory bonuses';            Re='VICTORY BONUS' }
    @{ Sec='Aborted ops';                Re='op ABORTED' }
    @{ Sec='Window ends';                Re='WINDOW END' }
    @{ Sec='Blockade gates released';    Re='GATES RELEASED' }
    @{ Sec='Units (strength vs FP)';     Re='Exiles units \[' }
    @{ Sec='Heg-League war switch';      Re='Heg-League WAR SWITCH: ' }
    @{ Sec='Heg-League states';          Re='Exiles Heg-League -> \d ' }
    @{ Sec='Heg-League tension';         Re='Exiles Heg-League tension ' }
    @{ Sec='Heg-League weariness';       Re='Exiles Heg-League weariness ' }
    @{ Sec='Heg-League ceasefires / containment'; Re='ceasefire|CONTAINED|RETALIATION exit|RETALIATION roll|engagement intel' }
    @{ Sec='Heg-League cadence re-rolls'; Re='HL interdiction cadence re-rolled' }
    # Pass 8a-1 (ExileHegLeaguePlayerStop): STOP = one line per finished player stop; STOP MARKS =
    # once per op per load, the first time its fleets are marked while the player is in the system.
    @{ Sec='Player stops (Pass 8a)';     Re='Exiles Heg-League STOP( MARKS)?: ' }
    # Test measurement (testing.stopDiagnosticsDays > 0): one line per op per interval while the
    # player shares the op's system -- each fleet's order, AI target, distances and hassle flags.
    @{ Sec='Player stop diagnostics (Pass 8a test)'; Re='Exiles Heg-League STOP DIAG: ' }
    # Interdiction objective keep-out (2026-11-18): OBJECTIVES = once per op per load, every
    # objective's distance to the nearest enemy station market; KEEP-OUT START / END = transitions.
    @{ Sec='Objective keep-outs (interdiction)'; Re='Exiles Heg-League \w+ OBJECTIVE(S at| KEEP-OUT)' }
    @{ Sec='Relief outcomes';            Re='RELIEF OUTCOME|RELIEF RECORDED|Ashen relief delivered' }
    @{ Sec='Ashen war clock';            Re='Ashen war clock|Ashen war score' }
    @{ Sec='Ashen sabotage hits';        Re='Ashen (ordinary|special) sabotage hit|SUCCESS$' }
    @{ Sec='Ashen rehearsal waits';      Re='Ashen rehearsal waiting' }
    @{ Sec='Watchdogs and ceilings';     Re='RETURN ORDER WATCHDOG|Extortion watchdog|exceeds the ceiling|no incursion-capable node|Lattice completion|Lattice construction COMPLETE' }
    @{ Sec='Omicron bombardments';       Re='Omicron performRaid' }
    @{ Sec='Omicron stations / Nexus';   Re='Omicron (asset|Nexus) (DESTROYED|REVIVED|REBUILT)|DECAPITATED|rebuild CANCELLED' }
    @{ Sec='Black Fleet sacks';          Re='Black Fleet assault: sacked' }
    @{ Sec='Extortion';                  Re='Extortion: (collectors dispatched|player killed)|Extortion cadence' }
    # Every paid line should be followed by a BOUNTY NOTICE line (BUG-2026-10-30-A check).
    @{ Sec='Bounties paid and notices';  Re='bounty: \d+c|BOUNTY NOTICE posted' }
    @{ Sec='Refugee convoys';            Re='Refugee convoy (launched|ordered|departed|left|ended|arrived)' }
    @{ Sec='Refugee salvage deferrals';  Re='Refugee salvage dispatch deferred' }
    @{ Sec='Midan ladder (Pass 7)';      Re='Exiles Midan(: module started| READY| WOULD LAUNCH| BLOCKED| LAUNCHED| RUNG RESOLVED| RUNG SKIPPED| HOLD:| RESUMES:| LANDING| STRIKE LANDED| AJAX LOST| AJAX CAPTAIN| DISTRESS CALL| ASSAULT ORDER| FORM UP| BOUNTY NOTICE| STAND DOWN| EVENTIDE| VOW| STRIKE OUTCOME| strike ABORTED| launch failed)' }
    @{ Sec='Midan measurements';         Re='Exiles Midan (COMPOSED|MEASURE|SCORE):' }
    @{ Sec='Midan proxy (7f)';           Re='Exiles Midan (DIRECTIVE|PROXY KILL|convoy killed by the Black Fleet)|Black Fleet AMBUSH|relief convoy began unloading|RELIEF CONVOY (LAUNCHED|BATTLE)' }
    @{ Sec='Story announcements';        Re='Exiles story announcement posted' }
    @{ Sec='Heg-League premise (B3, vanilla intel)'; Re='B3 self-heal|vanilla Hegemony/League hostility intel retired|Ending hostilities: Hegemony' }
    @{ Sec='Test pins';                  Re='TEST: |TEST PIN|forceState|forcePhase' }
)

# Timeline noise: data.scripts INFO lines matching these are left out of the timeline (and only
# counted). Everything else from data.scripts appears in the timeline.
$TimelineNoise = 'SALVAGE_DIAG|relief drip|Black Fleet logistics|Refugee fleet salvaged|director config loaded|Midan economy: joined|Black Fleet prosperity|Exiles units \[|feasibility: |Corsair Activity applied|Crusade Harassment applied|condition applied to|collateral|tier -?\d \(|HOLDING |Exiles Midan (COMPOSED|MEASURE|SCORE):|STOP DIAG: '

# Known vanilla/other-mod noise excluded from the error count.
$ErrorNoise = 'flare\]|module_hightech_decor|Starsector-IntelliJ-Template|MagicAutoTrails|MagicAchievementManager|lightmortar_fighter|Loading class:'

# ---------------------------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------------------------
foreach ($r in $OpRows)    { $r.Rx = New-Object System.Text.RegularExpressions.Regex($r.Re, 'Compiled') }
foreach ($r in $WatchRows) { $r.Rx = New-Object System.Text.RegularExpressions.Regex($r.Re, 'Compiled') }
$RxNoise   = New-Object System.Text.RegularExpressions.Regex($TimelineNoise, 'Compiled')
$RxErrNoise = New-Object System.Text.RegularExpressions.Regex($ErrorNoise, 'Compiled')
$RxDiag    = New-Object System.Text.RegularExpressions.Regex('SALVAGE_DIAG timestamp=(?<ts>-?\d+) date=(?<d>[A-Z][a-z]+ \d+, c\d+)', 'Compiled')
$RxSample  = New-Object System.Text.RegularExpressions.Regex('SAMPLE system=(?<sys>.+?) fields=.*?nex=(?<nex>\w+)', 'Compiled')
$RxMsg     = New-Object System.Text.RegularExpressions.Regex('^(?<ms>\d+)\s+\[[^\]]+\]\s+(?<lvl>[A-Z]+)\s+(?<cls>\S+)\s+-\s+(?<msg>.*)$', 'Compiled')

function Norm-Sys([string]$s) {
    if (-not $s) { return '' }
    $s = $s.Trim() -replace '\s+(Star System|star system)$', ''
    return $s.ToLowerInvariant()
}
# Date placeholders, resolved when the report is written (the next sample is known by then):
#   {{S:i}}  stale -- between sample i and sample i+1 ("Apr 17-29, c207"); "Apr 17, c207+" if none
#   {{N:k}}  logged after a reload, before its first sample -- the date of sample k
$RxPlaceholder = New-Object System.Text.RegularExpressions.Regex('\{\{(?<k>[SN]):(?<i>\d+)\}\}')
function Join-Dates([string]$a, [string]$b) {
    $ma = [regex]::Match($a, '^(?<m>[A-Z][a-z]+) (?<d>\d+), (?<c>c\d+)$')
    $mb = [regex]::Match($b, '^(?<m>[A-Z][a-z]+) (?<d>\d+), (?<c>c\d+)$')
    if (-not ($ma.Success -and $mb.Success)) { return "$a - $b" }
    if ($a -eq $b) { return $a }
    if ($ma.Groups['c'].Value -ne $mb.Groups['c'].Value) { return "$a - $b" }
    if ($ma.Groups['m'].Value -eq $mb.Groups['m'].Value) {
        return ($ma.Groups['m'].Value + ' ' + $ma.Groups['d'].Value + '-' + $mb.Groups['d'].Value + ', ' + $ma.Groups['c'].Value)
    }
    return ($ma.Groups['m'].Value + ' ' + $ma.Groups['d'].Value + ' - ' + $mb.Groups['m'].Value + ' ' + $mb.Groups['d'].Value + ', ' + $ma.Groups['c'].Value)
}
function Res([string]$s) {
    if ($null -eq $s -or -not $s.Contains('{{')) { return $s }
    return $RxPlaceholder.Replace($s, [System.Text.RegularExpressions.MatchEvaluator] {
        param($m)
        $i = [int]$m.Groups['i'].Value
        $a = $script:anchors
        if ($m.Groups['k'].Value -eq 'N') {
            if ($i -lt $a.Count) { return [string]$a[$i][0] } else { return '?' }
        }
        if ($i + 1 -lt $a.Count -and $a[$i + 1][1] -eq $a[$i][1]) { return (Join-Dates $a[$i][0] $a[$i + 1][0]) }
        return ([string]$a[$i][0] + '+')
    })
}
function Cell([string]$s, [int]$max = 160) {
    if ($null -eq $s) { return '' }
    $s = Res $s
    $s = $s -replace '\|', '/'
    if ($s.Length -gt $max) { $s = $s.Substring(0, $max) + '...' }
    return $s
}
# CASE-SENSITIVE on purpose (-cmatch): "failed=false" and "brokenByLosses=false" must not read as
# failures. The upper-case tokens are the ones the Exiles outcome lines print.
function Result-Class([string]$t) {
    if ($t -cmatch '\bFAILED\b|\(FAILURE\)|WITHOUT|op ABORTED|aborted=true|did NOT|success=false|failed=true|reachedPayload=false|brokenByLosses=true| BROKEN') { return 'FAIL' }
    if ($t -cmatch '\(SUCCESS\)|SUCCEEDED|LANDED|HELD|success=true|reachedPayload=true') { return 'OK' }
    return '?'
}
function Short-Msg([string]$m) {
    return ($m -replace '^Exiles (Heg-League|Ashen|Black Fleet|Omicron) ', '')
}

# ---------------------------------------------------------------------------------------------
# Pass 1: index launches and new games (cheap string checks only)
# ---------------------------------------------------------------------------------------------
function Index-File([string]$path) {
    $launches = New-Object System.Collections.Generic.List[int]
    $newGames = New-Object System.Collections.Generic.List[int]
    $n = 0
    $sr = New-Object System.IO.StreamReader($path)
    try {
        while ($null -ne ($line = $sr.ReadLine())) {
            $n++
            if ($line.Contains('Starting Starsector') -and $line.Contains('launcher')) { $launches.Add($n) }
            elseif ($line.Contains('onNewGameAfterEconomyLoad reached')) { $newGames.Add($n) }
        }
    } finally { $sr.Close() }
    return @{ Path = $path; Lines = $n; Launches = $launches; NewGames = $newGames }
}

if (-not (Test-Path $Log)) { throw "Log not found: $Log" }
$files = New-Object System.Collections.Generic.List[object]
$live = Index-File $Log
$files.Add($live)
$rotated = "$Log.1"
if ($Run -eq 'campaign' -and $live.NewGames.Count -eq 0 -and (Test-Path $rotated)) {
    $files.Insert(0, (Index-File $rotated))
}

# Global line numbering across the joined files.
$offset = 0
$allLaunch = New-Object System.Collections.Generic.List[int]
$allNew = New-Object System.Collections.Generic.List[int]
foreach ($f in $files) {
    $f.Offset = $offset
    foreach ($x in $f.Launches) { $allLaunch.Add($x + $offset) }
    foreach ($x in $f.NewGames) { $allNew.Add($x + $offset) }
    $offset += $f.Lines
}
$total = $offset

$startLine = 1; $endLine = $total; $runNote = ''
switch -regex ($Run) {
    '^campaign$' {
        if ($allNew.Count -gt 0) {
            $ng = $allNew[$allNew.Count - 1]
            # Start at the launch that contains the new game.
            $startLine = 1
            foreach ($l in $allLaunch) { if ($l -le $ng) { $startLine = $l } }
            $runNote = "campaign: new game at line $ng"
        } else {
            $runNote = 'campaign requested but NO new-game marker found -- reporting the whole file'
        }
    }
    '^session$' {
        if ($allLaunch.Count -gt 0) { $startLine = $allLaunch[$allLaunch.Count - 1] }
        $runNote = 'last session'
    }
    '^all$' { $runNote = 'whole file' }
    '^\d+$' {
        $i = [int]$Run
        if ($i -lt 1 -or $i -gt $allLaunch.Count) { throw "Session $i not found (1..$($allLaunch.Count))" }
        $startLine = $allLaunch[$i - 1]
        if ($i -lt $allLaunch.Count) { $endLine = $allLaunch[$i] - 1 }
        $runNote = "session $i"
    }
    default { throw "Unknown -Run value '$Run' (campaign | session | all | <n>)" }
}

# ---------------------------------------------------------------------------------------------
# Pass 2: parse the chosen range
# ---------------------------------------------------------------------------------------------
$date = '?'; $ts = $null; $where = '?'; $nexSeen = @{}
$anchorDate = '?'; $anchorMs = -1; $anchorTs = $null
$anchors = New-Object System.Collections.Generic.List[object]   # (date, launch index) per sample
$reloadsBack = New-Object System.Collections.Generic.List[object]   # (from date, to date, line)
# Real ms per game day, MEASURED from consecutive samples (the game speed varies: ~1,000 at x10,
# ~10,000 at x1). A date is "stale" once 1.5 game days of real time have passed since its sample --
# samples pause entirely while the player is in hyperspace.
$msPerDay = 1000.0
$firstDate = $null; $lastDate = $null
$sessionsIn = New-Object System.Collections.Generic.List[int]
$places = New-Object System.Collections.Generic.List[object]   # (date, system) on change
$ops = New-Object System.Collections.Generic.List[object]
$watch = @{}; foreach ($w in $WatchRows) { $watch[$w.Sec] = New-Object System.Collections.Generic.List[object] }
$timeline = New-Object System.Collections.Generic.List[object]
$errors = New-Object System.Collections.Generic.List[object]
$errNoiseCount = 0; $noiseCount = 0; $exilesLines = 0
$prevWasExilesErr = $false

function New-Op($row, $m, $dateNow, $tsNow, $whereNow, [bool]$orphan) {
    $op = $row.Op
    if ($m.Groups['op'].Success) { $op = $m.Groups['op'].Value.ToLowerInvariant() }
    if ($op -eq '*') { $op = '?' }
    return [pscustomobject]@{
        Dir = $row.Dir; Op = $op; Sys = (Norm-Sys $m.Groups['sys'].Value)
        SysName = $m.Groups['sys'].Value.Trim() -replace '\s+(Star System|star system)$', ''
        Tgt = $m.Groups['tgt'].Value.Trim(); Node = $m.Groups['node'].Value.Trim()
        LaunchDate = $dateNow; LaunchTs = $tsNow; LaunchText = ''
        Arrive = ''; Events = New-Object System.Collections.Generic.List[string]
        OutDate = ''; OutTs = $null; OutText = ''; Result = ''; OutWhere = ''
        Open = $true; Orphan = $orphan; ArrStr = 0; ArrDef = 0
    }
}

function Find-Op($row, $m, [bool]$openOnly) {
    $op = $row.Op
    if ($m.Groups['op'].Success) { $op = $m.Groups['op'].Value.ToLowerInvariant() }
    if ($row.Op -eq '*' -and $m.Groups['stage'].Success) {
        $st = $m.Groups['stage'].Value
        if ($st -match 'Squeeze') { $op = 'squeeze' } elseif ($st -match 'Strike') { $op = 'strike' } elseif ($st -match 'Interdiction') { $op = 'interdiction' }
    }
    $sys = Norm-Sys $m.Groups['sys'].Value
    $tgt = $m.Groups['tgt'].Value.Trim()
    $node = $m.Groups['node'].Value.Trim()
    $list = $script:ops
    $range = 0..($list.Count - 1)
    if (-not $openOnly) { $range = ($list.Count - 1)..0 }
    if ($list.Count -eq 0) { return $null }
    foreach ($i in $range) {
        $o = $list[$i]
        if ($o.Dir -ne $row.Dir) { continue }
        if ($openOnly -and -not $o.Open) { continue }
        if (-not $openOnly -and $o.Open) { continue }
        if ($op -ne '*' -and $o.Op -ne $op) { continue }
        if ($row.Any) { return $o }
        if ($node -and $o.Node -eq $node) { return $o }
        if ($sys -and $o.Sys -eq $sys) { return $o }
        if ($tgt -and $o.Tgt -and $o.Tgt -eq $tgt) { return $o }
    }
    return $null
}

$g = 0
foreach ($f in $files) {
    $fileStart = $f.Offset + 1; $fileEnd = $f.Offset + $f.Lines
    if ($fileEnd -lt $startLine -or $fileStart -gt $endLine) { continue }
    $sr = New-Object System.IO.StreamReader($f.Path)
    try {
        $g = $f.Offset
        while ($null -ne ($line = $sr.ReadLine())) {
            $g++
            if ($g -lt $startLine) { continue }
            if ($g -gt $endLine) { break }

            if ($line.Contains('Starting Starsector') -and $line.Contains('launcher')) {
                $sessionsIn.Add($g); $anchorMs = -1   # the ms prefix restarts with each launch
                continue
            }

            # Errors: Exiles WARN/ERROR lines, and exceptions whose next lines are Exiles frames.
            if ($line.Contains(' ERROR ') -or $line.Contains(' WARN ')) {
                if ($RxErrNoise.IsMatch($line)) { $errNoiseCount++ }
                elseif ($line.Contains('data.scripts.') -or $line.Contains('Exiles')) { $errors.Add(@($date, $where, $line)) }
                else { $errNoiseCount++ }
            }
            if ($line.StartsWith("`tat data.scripts.") -and -not $prevWasExilesErr) {
                $errors.Add(@($date, $where, "stack frame: " + $line.Trim()))
                $prevWasExilesErr = $true
                continue
            }
            if (-not $line.StartsWith("`tat ")) { $prevWasExilesErr = $false }

            if (-not $line.Contains('data.scripts.')) { continue }
            $mm = $RxMsg.Match($line)
            if (-not $mm.Success) { continue }
            if ($mm.Groups['lvl'].Value -ne 'INFO') { continue }
            # Only lines LOGGED BY Exiles classes (the script loader's "Loading class: data.scripts..."
            # lines mention the package but come from com.fs.starfarer).
            if (-not $mm.Groups['cls'].Value.StartsWith('data.scripts.')) { continue }
            $msg = $mm.Groups['msg'].Value
            $ms = [long]$mm.Groups['ms'].Value

            # Date label: the last SALVAGE_DIAG date, marked "+" (= on or after) once that anchor is
            # stale (see $msPerDay) or from an earlier launch -- the samples can pause for weeks of
            # game time, and a carried-forward date must not pose as exact.
            if ($anchorMs -lt 0) { $date = '{{N:' + $anchors.Count + '}}' }
            elseif (($ms - $anchorMs) -gt 1.5 * $msPerDay) { $date = '{{S:' + ($anchors.Count - 1) + '}}' }
            else { $date = $anchorDate }

            if ($msg.Contains('SALVAGE_DIAG')) {
                $d = $RxDiag.Match($msg)
                if ($d.Success) {
                    $newTs = [double]$d.Groups['ts'].Value
                    if ($anchorMs -ge 0 -and $null -ne $anchorTs) {
                        $dDays = ($newTs - $anchorTs) / 86400000.0
                        if ($dDays -ge 0.4 -and $dDays -le 3.0 -and $ms -gt $anchorMs) {
                            $msPerDay = 0.7 * $msPerDay + 0.3 * (($ms - $anchorMs) / $dDays)
                        }
                    }
                    # A reload of an OLDER save: game time jumps back. Ops launched after the save
                    # point were rolled back in game, but their launch lines stay in the log.
                    if ($null -ne $ts -and $newTs -lt $ts - 86400000.0) {
                        $reloadsBack.Add(@($lastDate, $d.Groups['d'].Value, $g))
                    }
                    $anchorDate = $d.Groups['d'].Value; $anchorMs = $ms; $anchorTs = $newTs; $date = $anchorDate
                    $anchors.Add(@($anchorDate, $sessionsIn.Count))
                    $ts = $newTs
                    if (-not $firstDate) { $firstDate = $date }
                    $lastDate = $date
                }
                $s = $RxSample.Match($msg)
                if ($s.Success) {
                    $nw = $s.Groups['sys'].Value -replace '\s+Star System$', ''
                    $nexSeen[$s.Groups['nex'].Value] = $true
                    if ($nw -ne $where) { $where = $nw; $places.Add(@($date, $where)) }
                }
                continue
            }
            $exilesLines++

            # Op rows (first match wins).
            foreach ($row in $OpRows) {
                $m = $row.Rx.Match($msg)
                if (-not $m.Success) { continue }
                $short = Short-Msg $msg
                if ($row.Ev -eq 'launch') {
                    $o = New-Op $row $m $date $ts $where $false
                    $bits = @()
                    if ($m.Groups['src'].Success) { $bits += ($m.Groups['src'].Value + ' -> ' + $(if ($o.Tgt) { $o.Tgt + ' @ ' } else { '' }) + $o.SysName) }
                    elseif ($o.Tgt -and $o.SysName) { $bits += ($o.Tgt + ' @ ' + $o.SysName) }
                    elseif ($o.Tgt) { $bits += $o.Tgt }
                    if ($m.Groups['actor'].Success) { $bits += ($m.Groups['actor'].Value + '>' + $m.Groups['victim'].Value) }
                    if ($m.Groups['fp'].Success) { $bits += ('FP ' + $m.Groups['fp'].Value) }
                    if ($m.Groups['def'].Success) { $bits += ('def ' + $m.Groups['def'].Value) }
                    if ($m.Groups['gate'].Success) { $bits += ('gate ' + $m.Groups['gate'].Value) }
                    if ($m.Groups['node'].Success) { $bits += ('node ' + $o.Node) }
                    $o.LaunchText = ($bits -join ', ')
                    $ops.Add($o)
                } else {
                    $o = Find-Op $row $m $true
                    if (-not $o) { $o = Find-Op $row $m $false }
                    if (-not $o) {
                        $o = New-Op $row $m '?' $null '?' $true
                        $o.LaunchText = '(launch not in range)'
                        $ops.Add($o)
                    }
                    if ($row.Ev -eq 'retleg') {
                        # The return-leg line (since 2026-10-06) is the ONLY trace of an op that
                        # failed before its action stage; when the op is still open and the line says
                        # "failed at stage N", it is the outcome. Otherwise it is an ordinary event.
                        if ($o.Open -and $msg -match 'failed at stage') {
                            $o.OutDate = $date; $o.OutTs = $ts; $o.OutWhere = $where
                            $o.OutText = $short; $o.Result = 'FAIL'; $o.Open = $false
                        } else {
                            $o.Events.Add("$date [$where] " + $short)
                        }
                    } elseif ($row.Ev -eq 'arrive') {
                        $o.Arrive = "$date : str $($m.Groups['str'].Value) vs def $($m.Groups['def'].Value)"
                        $o.ArrStr = [int]$m.Groups['str'].Value; $o.ArrDef = [int]$m.Groups['def'].Value
                    } elseif ($row.Ev -eq 'event') {
                        $tag = $short
                        if ($m.Groups['pl'].Success) { $tag = $tag }
                        $o.Events.Add("$date [$where] " + $tag)
                    } else {
                        if ($o.Open) {
                            $o.OutDate = $date; $o.OutTs = $ts; $o.OutWhere = $where
                            $o.OutText = $short; $o.Result = Result-Class $msg; $o.Open = $false
                        } else {
                            $o.Events.Add("$date [$where] (late) " + $short)
                            if ((Result-Class $msg) -eq 'FAIL') { $o.Result = 'FAIL' }
                        }
                    }
                }
                break
            }

            foreach ($w in $WatchRows) {
                if ($w.Rx.IsMatch($msg)) { $watch[$w.Sec].Add(@($date, $where, (Short-Msg $msg))) }
            }

            if ($RxNoise.IsMatch($msg)) { $noiseCount++ }
            elseif (-not $NoTimeline) { $timeline.Add(@($date, $where, ($mm.Groups['cls'].Value -replace '^data\.scripts\.\w+\.', ''), $msg)) }
        }
    } finally { $sr.Close() }
}

# ---------------------------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------------------------
if (-not $Out) {
    $Out = Join-Path (Join-Path $PSScriptRoot '..\..\..\logs-archive') ("report_" + (Get-Date -Format 'yyyy-MM-dd_HHmm') + ".md")
}
$sb = New-Object System.Text.StringBuilder
function W([string]$s) { [void]$script:sb.AppendLine((Res $s)) }

W "# Exiles log report"
W ""
W ("Generated " + (Get-Date -Format 'yyyy-MM-dd HH:mm') + " by tools/exiles-log-report.ps1 (read-only).")
W ""
W "## Run"
W ""
W ("- **Files:** " + (($files | ForEach-Object { $_.Path }) -join ' + '))
W ("- **Range:** lines $startLine-$endLine of $total ($runNote)")
W ("- **Launches in range:** " + $sessionsIn.Count + $(if ($sessionsIn.Count -gt 0) { ' (at lines ' + ($sessionsIn -join ', ') + ')' } else { '' }))
$nexText = '?'
if ($nexSeen.Count -gt 0) { $nexText = ($nexSeen.Keys -join ', ') }
W ("- **Nexerelin:** " + $nexText + "  (from SALVAGE_DIAG ``nex=``)")
if ($firstDate) { W ("- **In-game dates:** $firstDate -> $lastDate") }
else { W "- **In-game dates:** ? (no SALVAGE_DIAG lines: dates and player locations print as ?)" }
W "- **Dates:** from SALVAGE_DIAG samples. Samples pause (e.g. in hyperspace), so a line logged during a pause shows the bracketing range (``Apr 17-29, c207``), or ``Apr 17, c207+`` if no later sample exists; ``~`` on a day count means it spans such a range."
W ("- **Exiles errors/warnings:** " + $errors.Count + " (vanilla/other-mod noise excluded: $errNoiseCount)")
W ("- **Exiles log lines:** $exilesLines (timeline noise filtered: $noiseCount)")
if ($reloadsBack.Count -gt 0) {
    W ("- **Reloads of an earlier save:** " + (($reloadsBack | ForEach-Object { $_[0] + ' -> ' + $_[1] + ' (line ' + $_[2] + ')' }) -join '; ') +
       ". Ops launched in the rolled-back stretch can show as open or orphaned: the game forgot them, the log did not.")
}
W ""
if ($places.Count -gt 0) {
    W "**Player location** (from half-day samples; changes only):"
    W ""
    W ("> " + (($places | ForEach-Object { $_[0] + ' ' + $_[1] }) -join '  ->  '))
    W ""
}

# Summary counts
W "## Summary"
W ""
W "| Director | Op | Launched | OK | FAIL | ? | Still open | Orphan outcomes |"
W "|---|---|---|---|---|---|---|---|"
$groups = $ops | Group-Object { $_.Dir + '|' + $_.Op } | Sort-Object Name
foreach ($grp in $groups) {
    $parts = $grp.Name.Split('|')
    $l = @($grp.Group | Where-Object { -not $_.Orphan }).Count
    $ok = @($grp.Group | Where-Object { $_.Result -eq 'OK' }).Count
    $fl = @($grp.Group | Where-Object { $_.Result -eq 'FAIL' }).Count
    $un = @($grp.Group | Where-Object { -not $_.Open -and $_.Result -eq '?' }).Count
    $op = @($grp.Group | Where-Object { $_.Open }).Count
    $or = @($grp.Group | Where-Object { $_.Orphan }).Count
    W "| $($parts[0]) | $($parts[1]) | $l | $ok | $fl | $un | $op | $or |"
}
W ""

# Anomalies
W "## Anomalies"
W ""
$anom = 0
foreach ($o in $ops) {
    if ($o.Result -eq 'FAIL' -and $o.ArrDef -gt 0 -and $o.ArrStr -ge 3 * $o.ArrDef) {
        $why = ''
        if (@($o.Events | Where-Object { $_ -match 'VICTORY BONUS' }).Count -gt 0) { $why = ' -- **the player broke it** (victory bonus line)' }
        elseif ($o.OutWhere -and $o.OutWhere -eq $o.SysName) { $why = ' -- the player was in the system (live fleets, live fight)' }
        W ("- **Strong arrival but FAILED:** $($o.Dir) $($o.Op) at $($o.SysName) -- arrival str $($o.ArrStr) vs def $($o.ArrDef); outcome: " + (Cell $o.OutText 200) + " (player: $($o.OutWhere))" + $why)
        $anom++
    }
    if ($o.Open) {
        $age = ''
        if ($null -ne $o.LaunchTs -and $null -ne $ts) {
            $ad = [math]::Round(($ts - $o.LaunchTs) / 86400000.0)
            if ($ad -le 120) { $age = " -- launched $ad days before the last sample: probably still running" }
            else { $age = " -- **$ad days before the last sample: stuck, or ended without an outcome line**" }
        }
        W ("- **No outcome by end of range:** $($o.Dir) $($o.Op) launched $($o.LaunchDate) -> $($o.SysName) ($($o.LaunchText))" + $age); $anom++
    }
    if ($o.Orphan) { W ("- **Outcome without launch in range:** $($o.Dir) $($o.Op) at $($o.SysName) -- " + (Cell $o.OutText 160)); $anom++ }
}
if ($anom -eq 0) { W "None." }
W ""

# Op ledgers
W "## Op ledger"
foreach ($dir in @('Heg-League', 'Midan', 'Ashen', 'Black Fleet', 'Omicron')) {
    $list = @($ops | Where-Object { $_.Dir -eq $dir })
    if ($list.Count -eq 0) { continue }
    W ""
    W "### $dir"
    W ""
    W "| Op | Launched | Launch | Arrival | Outcome date | Result | Days | Player at outcome | Outcome / events |"
    W "|---|---|---|---|---|---|---|---|---|"
    foreach ($o in $list) {
        $days = ''
        if ($null -ne $o.LaunchTs -and $null -ne $o.OutTs) {
            $days = [string][math]::Round(($o.OutTs - $o.LaunchTs) / 86400000.0)
            if ($o.LaunchDate.Contains('{{') -or $o.OutDate.Contains('{{')) { $days = '~' + $days }
        }
        $res = $o.Result; if ($o.Open) { $res = 'open' }
        $detail = $o.OutText
        if ($o.Events.Count -gt 0) { $detail = $detail + ' // ' + ($o.Events -join ' // ') }
        W ("| $($o.Op) | $($o.LaunchDate) | " + (Cell $o.LaunchText 120) + " | " + (Cell $o.Arrive 60) + " | $($o.OutDate) | **$res** | $days | $($o.OutWhere) | " + (Cell $detail 600) + " |")
    }
}
W ""

# Watch list
W "## Watch list"
foreach ($w in $WatchRows) {
    $items = $watch[$w.Sec]
    W ""
    W ("### " + $w.Sec + " (" + $items.Count + ")")
    if ($items.Count -eq 0) { continue }
    W ""
    $max = 60
    $shown = 0
    foreach ($it in $items) {
        if ($shown -ge $max) { W ("- ... " + ($items.Count - $max) + " more"); break }
        W ("- " + $it[0] + " [" + $it[1] + "] " + (Cell $it[2] 300))
        $shown++
    }
}
W ""

# Errors
W "## Exiles errors and warnings"
W ""
if ($errors.Count -eq 0) { W "None." }
foreach ($e in $errors) { W ("- " + $e[0] + " [" + $e[1] + "] " + (Cell $e[2] 300)) }
W ""

if (-not $NoTimeline) {
    W "## Timeline"
    W ""
    W "Every Exiles director line in range except the noise filter (see the script's ``TimelineNoise``)."
    W ""
    W "| Date | Player | Class | Line |"
    W "|---|---|---|---|"
    foreach ($t in $timeline) { W ("| " + $t[0] + " | " + $t[1] + " | " + $t[2] + " | " + (Cell $t[3] 260) + " |") }
}

$dir = Split-Path $Out -Parent
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Force $dir | Out-Null }
[System.IO.File]::WriteAllText($Out, $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))

# Console summary
Write-Output ("Report: " + $Out)
Write-Output ("Range: lines $startLine-$endLine ($runNote); dates " + $(if ($firstDate) { "$firstDate -> $lastDate" } else { '?' }))
Write-Output ("Ops: " + $ops.Count + " (open " + @($ops | Where-Object { $_.Open }).Count + ", orphan " + @($ops | Where-Object { $_.Orphan }).Count + "); anomalies: $anom; Exiles errors: " + $errors.Count)

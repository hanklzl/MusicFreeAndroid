// maestro/flows/parity/_lib/mark_waypoint.js
// usage: runScript with env: { WAYPOINT, EDGE (BEGIN|END), DEVICE }
// 在 logcat 打稳定锚点，parse_events.py 据此切分 waypoint 窗口。

const waypoint = MAESTRO_ENV.WAYPOINT;
const edge     = MAESTRO_ENV.EDGE || "BEGIN";
const device   = MAESTRO_ENV.DEVICE ? `-s ${MAESTRO_ENV.DEVICE}` : "";

const cmd = `adb ${device} shell log -t PARITY_MARK "${edge} ${waypoint}"`;
const result = shell(cmd);
output.exitCode = result.exitCode;

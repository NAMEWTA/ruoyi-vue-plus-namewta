#!/usr/bin/env node
'use strict';

const TIME_ZONE = 'Asia/Shanghai';

function formatTimestamp(date) {
    const formatter = new Intl.DateTimeFormat('en-CA', {
        timeZone: TIME_ZONE,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hourCycle: 'h23'
    });
    const parts = Object.fromEntries(
        formatter.formatToParts(date)
            .filter((part) => part.type !== 'literal')
            .map((part) => [part.type, part.value])
    );
    return `${parts.year}-${parts.month}-${parts.day}_${parts.hour}:${parts.minute}:${parts.second}`;
}

if (process.argv.length > 2) {
    process.stderr.write('用法: node generate-change-timestamp.js\n');
    process.exitCode = 1;
} else {
    process.stdout.write(`${formatTimestamp(new Date())}\n`);
}

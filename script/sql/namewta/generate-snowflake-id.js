#!/usr/bin/env node
'use strict';

const { randomInt } = require('node:crypto');
const { networkInterfaces } = require('node:os');

const TWEPOCH = 1288834974657n;
const MAX_WORKER_ID = 31n;
const MAX_DATACENTER_ID = 31n;
const SEQUENCE_MASK = 4095n;
const WORKER_ID_SHIFT = 12n;
const DATACENTER_ID_SHIFT = 17n;
const TIMESTAMP_LEFT_SHIFT = 22n;
const MAX_CLOCK_BACKWARD_MS = 5n;
const MAX_COUNT = 100000;
const SLEEP_BUFFER = new Int32Array(new SharedArrayBuffer(4));

function usage() {
    return [
        '用法:',
        '  node generate-snowflake-id.js',
        '  node generate-snowflake-id.js --count <1-100000>',
        '  node generate-snowflake-id.js --help'
    ].join('\n');
}

function parseCount(args) {
    if (args.length === 0) {
        return 1;
    }
    if (args.length === 1 && (args[0] === '--help' || args[0] === '-h')) {
        process.stdout.write(`${usage()}\n`);
        return null;
    }
    if (args.length !== 2 || args[0] !== '--count') {
        throw new Error(`参数无效\n${usage()}`);
    }
    const count = Number(args[1]);
    if (!Number.isSafeInteger(count) || count < 1 || count > MAX_COUNT) {
        throw new Error(`--count 必须是 1 到 ${MAX_COUNT} 之间的整数`);
    }
    return count;
}

function isUsableMac(mac) {
    return typeof mac === 'string'
        && /^([0-9a-f]{2}:){5}[0-9a-f]{2}$/i.test(mac)
        && mac.toLowerCase() !== '00:00:00:00:00:00';
}

function isSiteLocalIpv4(address) {
    const octets = address.split('.').map(Number);
    if (octets.length !== 4 || octets.some((value) => !Number.isInteger(value) || value < 0 || value > 255)) {
        return false;
    }
    return octets[0] === 10
        || (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31)
        || (octets[0] === 192 && octets[1] === 168);
}

function selectMacAddress() {
    const candidates = [];
    for (const addresses of Object.values(networkInterfaces())) {
        for (const address of addresses ?? []) {
            if (!address.internal && isUsableMac(address.mac)) {
                candidates.push(address);
            }
        }
    }
    const siteLocal = candidates.find(
        (address) => address.family === 'IPv4' && isSiteLocalIpv4(address.address)
    );
    return (siteLocal ?? candidates[0])?.mac ?? null;
}

function deriveDatacenterId() {
    const mac = selectMacAddress();
    if (mac === null) {
        return 1n;
    }
    const bytes = mac.split(':').map((part) => Number.parseInt(part, 16));
    const combined = bytes[bytes.length - 2] | (bytes[bytes.length - 1] << 8);
    return BigInt(combined >> 6) % (MAX_DATACENTER_ID + 1n);
}

function javaStringHash(value) {
    let hash = 0;
    for (let index = 0; index < value.length; index += 1) {
        hash = (Math.imul(hash, 31) + value.charCodeAt(index)) | 0;
    }
    return hash;
}

function deriveWorkerId(datacenterId) {
    const pid = process.pid < 10 ? randomInt(10, 4194304) : process.pid;
    const hash = javaStringHash(`${datacenterId}${pid}`) & 0xffff;
    return BigInt(hash) % (MAX_WORKER_ID + 1n);
}

function sleep(milliseconds) {
    if (milliseconds > 0) {
        Atomics.wait(SLEEP_BUFFER, 0, 0, milliseconds);
    }
}

class MybatisPlusSnowflake {
    constructor(workerId, datacenterId) {
        this.workerId = workerId;
        this.datacenterId = datacenterId;
        this.sequence = 0n;
        this.lastTimestamp = -1n;
    }

    nextId() {
        let timestamp = BigInt(Date.now());
        if (timestamp < this.lastTimestamp) {
            const offset = this.lastTimestamp - timestamp;
            if (offset > MAX_CLOCK_BACKWARD_MS) {
                throw new Error(`系统时钟回拨 ${offset}ms，拒绝生成 ID`);
            }
            sleep(Number(offset << 1n));
            timestamp = BigInt(Date.now());
            if (timestamp < this.lastTimestamp) {
                throw new Error(`系统时钟回拨 ${offset}ms，等待后仍未恢复`);
            }
        }

        if (timestamp === this.lastTimestamp) {
            this.sequence = (this.sequence + 1n) & SEQUENCE_MASK;
            if (this.sequence === 0n) {
                timestamp = this.waitUntilNextMillis(this.lastTimestamp);
            }
        } else {
            this.sequence = BigInt(randomInt(1, 3));
        }

        this.lastTimestamp = timestamp;
        return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
            | (this.datacenterId << DATACENTER_ID_SHIFT)
            | (this.workerId << WORKER_ID_SHIFT)
            | this.sequence;
    }

    waitUntilNextMillis(lastTimestamp) {
        let timestamp = BigInt(Date.now());
        while (timestamp <= lastTimestamp) {
            sleep(1);
            timestamp = BigInt(Date.now());
        }
        return timestamp;
    }
}

function main() {
    const count = parseCount(process.argv.slice(2));
    if (count === null) {
        return;
    }
    const datacenterId = deriveDatacenterId();
    const workerId = deriveWorkerId(datacenterId);
    const generator = new MybatisPlusSnowflake(workerId, datacenterId);
    const ids = Array.from({ length: count }, () => generator.nextId().toString());
    process.stdout.write(`${ids.join('\n')}\n`);
}

try {
    main();
} catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : String(error)}\n`);
    process.exitCode = 1;
}

import { test } from "node:test";
import assert from "node:assert/strict";
import { barecrop, indexed, playlist } from "../src/decoders";
import {
  nextPages,
  streamOptions,
  resolve,
  discover,
} from "../src/resolver";
import { destination, publicAddress } from "../src/destination";
import { getEvents, parseCatalog, parseCountries } from "../src/catalog";
import { sports, channelsFor, countdown, section } from "../src/model";
import { PROVIDER_BASE, USER_AGENT } from "../src/provider";

const media = "https://cdn.example.test/live/channel.m3u8?expires=2000000000";
function envelope(value: unknown) {
  let json = JSON.stringify(value);
  while (Buffer.from(json).toString("base64").length % 4) json += " ";
  const data = Buffer.from(json).toString("base64");
  const size = data.length / 4;
  const pieces = [2, 0, 3, 1].map((i) => {
    const encoded = Buffer.from(data.slice(i * size, (i + 1) * size)).toString(
      "base64",
    );
    return encoded.slice(0, 3) + "x" + encoded.slice(3);
  });
  return `window._econfig = "${Buffer.from(pieces.join("")).toString("base64")}";`;
}
function indexedHtml(name = "rotatingData", url = media) {
  const pairs = [...url]
    .map((c, i) => [
      i,
      Buffer.from(`x${c.charCodeAt(0) + 21}x`).toString("base64"),
    ])
    .reverse();
  return `var ${name}=${JSON.stringify(pairs)}; function alpha(){return 9;} function beta(){return 12;} var k=alpha()+beta();
${name}.sort((a,b) => a[0]-b[0]); ${name}.forEach(e => {let v=e[1];playbackURL += String.fromCharCode(parseInt(atob(v).replace(/\u005cD/g, '')) - k)}); new Clappr.Player({source: playbackURL,});`;
}
test("econfig prefers native URL and rejects broken envelopes", () => {
  assert.equal(
    barecrop(
      envelope({
        stream_url: "https://peer.example.test/p2p.m3u8",
        stream_url_nop2p: media,
      }),
    ),
    media,
  );
  assert.equal(
    barecrop(envelope({ stream_url: media, stream_url_nop2p: " " })),
    media,
  );
  assert.equal(barecrop('window._econfig="AAAA"'), null);
  assert.equal(
    playlist(envelope({ stream_url: "http://localhost/a.m3u8" })),
    null,
  );
});
test("indexed format survives rotating identifiers but rejects changed operations", () => {
  for (const name of ["dataOne", "anotherName123", "data_$42"])
    assert.equal(indexed(indexedHtml(name)), media);
  assert.equal(indexed(indexedHtml().replace("a[0]-b[0]", "b[0]-a[0]")), null);
  assert.equal(
    indexed(indexedHtml().replace("source: playbackURL", "source: other")),
    null,
  );
  assert.equal(
    indexed(indexedHtml().replace("return 12;", "return random();")),
    null,
  );
});
test("literal and joined configs parse data without evaluating expressions", () => {
  assert.equal(
    playlist(
      `var playbackURL = '${media}'; new Clappr.Player({source: playbackURL,});`,
    ),
    media,
  );
  assert.equal(
    playlist(
      `var playbackURL = '${media}' + evil(); new Clappr.Player({source: playbackURL,});`,
    ),
    null,
  );
  assert.equal(
    playlist(
      `return (["https://cdn.example.test/", "live/channel.m3u8"].join("") + tail.join("")); var tail=["?expires=2000000000"];`,
    ),
    media,
  );
  assert.equal(
    playlist('return (["https://cdn.example.test/a.m3u8"].join("") + evil());'),
    null,
  );
  assert.equal(
    playlist(
      `<div id="token">?expires=2000000000</div><script>return (["https://cdn.example.test/live/channel.m3u8"].join("") + document.getElementById('token').innerHTML);</script>`,
    ),
    media,
  );
});
test("discovery ranks player evidence and excludes hidden ads, without a host list", () => {
  const candidates = nextPages(
    `<iframe src="https://other.test/frame"></iframe><aside class="advert"><iframe allowfullscreen src="https://ads.test/video"></iframe></aside><iframe hidden src="https://hidden.test/frame"></iframe><iframe width="1" src="https://pixel.test/frame"></iframe><iframe allowfullscreen data-src="https://new-host.test/player"></iframe>`,
    PROVIDER_BASE,
  );
  assert.deepEqual(candidates, [
    "https://new-host.test/player",
    "https://other.test/frame",
  ]);
  assert.deepEqual(
    nextPages(
      '<script src="https://igniteandship.com/wiki.js"></script><script>fid="tennis_1"</script>',
      PROVIDER_BASE,
    ),
    ["https://igniteandship.com/wiki.php?player=desktop&live=tennis_1"],
  );
  assert.deepEqual(
    nextPages('<iframe src="https:///wiki.php"></iframe>', PROVIDER_BASE),
    [],
  );
});
test("stream options are numbered, ordered, same-host, and distinct", () => {
  assert.deepEqual(
    streamOptions(
      '<a href="/s2">Stream 2</a><a href="/s1">STREAM #1</a><a href="/s2">Stream 2</a><a href="https://ad.test/">Stream 3</a>',
      PROVIDER_BASE,
    ).map((l) => l.url),
    [PROVIDER_BASE + "s1", PROVIDER_BASE + "s2"],
  );
});
test("selected stream 2 resolves only its embeds, preserving final-player headers", async () => {
  const selected = PROVIDER_BASE + "s2";
  const player = "https://rotating-host.test/embed";
  const calls: { url: string; headers: Record<string, string> }[] = [];
  const client = async (url: string, headers: Record<string, string> = {}) => {
    calls.push({ url, headers });
    const bodies: Record<string, string> = {
      [selected]: `<a href="/s1">Stream 1</a><a href="/s2">Stream 2</a><iframe src="${player}" allowfullscreen></iframe>`,
      [player]: envelope({ stream_url_nop2p: media }),
      [media]: "#EXTM3U\n#EXTINF:8,\nchunk.ts",
    };
    if (!bodies[url]) throw new Error("Unexpected request");
    return { url, body: bodies[url] };
  };
  const result = await resolve(
    { label: "Stream 2", url: selected },
    undefined,
    client,
  );
  assert.deepEqual(
    calls.map((c) => c.url),
    [selected, player, media],
  );
  assert.deepEqual(result.headers, {
    Origin: "https://rotating-host.test",
    Referer: "https://rotating-host.test/",
    "User-Agent": USER_AGENT,
  });
  assert.equal(calls[1].headers.Referer, selected);
  assert.deepEqual(calls[2].headers, result.headers);
  assert.equal(result.expiresAt, 2000000000000);
});
test("discovery returns options without touching signed playlists", async () => {
  const result = await discover(
    {
      name: "Example",
      links: [{ label: "Example", url: PROVIDER_BASE + "channel/" }],
    },
    undefined,
    async (url) => ({
      url,
      body: '<a href="/one/">Stream 1</a><a href="/two/">Stream 2</a>',
    }),
  );
  assert.equal(result.length, 2);
  assert.equal(result[1].url, PROVIDER_BASE + "two/");
});
test("traversal stays bounded and aborts obsolete selections", async () => {
  let reads = 0;
  const client = async (url: string) => {
    ++reads;
    return {
      url,
      body: Array.from(
        { length: 15 },
        (_, i) => `<iframe src="${url}/${i}" allowfullscreen></iframe>`,
      ).join(""),
    };
  };
  await assert.rejects(
    resolve(
      { label: "Stream 1", url: "https://player.test/root" },
      undefined,
      client,
    ),
  );
  assert.equal(reads, 12);
  const aborted = new AbortController();
  aborted.abort();
  reads = 0;
  await assert.rejects(
    resolve(
      { label: "Stream 1", url: "https://player.test/root" },
      aborted.signal,
      client,
    ),
  );
  assert.equal(reads, 0);
});
test("destinations reject local, malformed and insecure URLs", () => {
  for (const url of [
    "http://player.test/video",
    "https:///wiki.php",
    "https://user:pw@player.test/a",
    "https://localhost/a",
    "https://127.0.0.1/a",
    "https://10.0.0.2/a",
    "https://[::1]/a",
    "https://[::ffff:127.0.0.1]/a",
    "https://device.local/a",
  ])
    assert.throws(() => destination(url), url);
  assert.equal(
    destination("https://new-player.test/a").hostname,
    "new-player.test",
  );
  assert.equal(publicAddress("8.8.8.8"), true);
  assert.equal(publicAddress("192.168.1.4"), false);
  assert.equal(publicAddress("100.64.0.1"), false);
});
test("schedule parsing preserves timestamps, league art and channel labels", () => {
  const html = `<h2>SEPTEMBER 13, 2026</h2><table><tr data-timestamp="1789316100"><td class="matchtime">15:15</td><td><img class="leagueimg" src="/league.png"><span class="leaguename">Primera Division</span><span class="event-title">Home v Away</span><a href="/sports/">Sky Sports</a><a href="/sports-alt/">Sky Sports #2</a></td></tr></table>`;
  const events = parseCatalog(html, PROVIDER_BASE);
  assert.equal(events.length, 1);
  assert.equal(events[0].startsAt, 1789316100000);
  assert.equal(events[0].leagueIconUrl, PROVIDER_BASE + "league.png");
  assert.equal(channelsFor(events[0])[0].links.length, 2);
  assert.equal(
    section(events[0], sports[0], events[0].startsAt! + 1000),
    "Current",
  );
  assert.equal(countdown(89 * 60_000, 0), "in 1h29");
});
test("LiveTV prioritizes FR US UK and ignores off-site channels", () => {
  const html = ["DE", "UK", "US", "FR"]
    .map(
      (code) =>
        `<div class="dropdown"><button class="dropbtn">${code}</button><div class="dropdown-content"><a href="/${code}/">${code} TV</a><a href="https://ads.test/">Ad</a></div></div>`,
    )
    .join("");
  const result = parseCountries(html, PROVIDER_BASE);
  assert.deepEqual(
    result.map((c) => c.code),
    ["FR", "US", "UK", "DE"],
  );
  assert.equal(result[0].channels.length, 1);
  assert.equal(result[2].name, "United Kingdom");
});

test("NBA with only WNBA listings is an empty schedule, not a load failure", async () => {
  const body = `<h2>WNBA STREAMS</h2><h3>AUGUST 30, 2026</h3>
    <section class="elementor-top-section"><h3 class="teamz">Home @ Away</h3>
      <p>3:00 PM ET</p><a href="/basketball-game/">Watch</a></section>`;
  const client = async (url: string) => ({ url, body });
  assert.deepEqual(await getEvents("nba", undefined, client), []);
  assert.equal((await getEvents("basketball", undefined, client)).length, 1);
});

test("unrecognized schedule pages still report a loading failure", async () => {
  await assert.rejects(
    getEvents("nba", undefined, async (url) => ({
      url,
      body: "<h1>Unavailable</h1>",
    })),
    /No schedule available/,
  );
});

test("Giants stream 1 resolves its literal array source without selecting alternatives", async () => {
  const selected = "https://freestreams-live1h.pk/new-york-giants-live-stream/";
  const wrapper = "https://wikisport.info/nfl0/";
  const stream1 = wrapper + "01.php";
  const player = "https://in-stream.click/embed/giants";
  const calls: string[] = [];
  const bodies: Record<string, string> = {
    [selected]: `<iframe src="${wrapper}" allowfullscreen></iframe>`,
    [wrapper]: `<a href="01.php">Stream 1</a><a href="012.php">Stream 2</a><iframe src="01.php" allowfullscreen></iframe>`,
    [stream1]: `<iframe src="${player}" allowfullscreen></iframe>`,
    [player]: `const streamUrls = ["${media}", "https://other.test/alternative.m3u8"]; player.setup({file: streamUrls[0], autostart: false});`,
    [media]: "#EXTM3U\n#EXTINF:8,\nsegment.ts",
  };
  const result = await resolve(
    { label: "Stream 1", url: selected },
    undefined,
    async (url, headers) => {
      calls.push(url);
      assert.ok(bodies[url], "Unexpected request");
      if (url === media)
        assert.equal(headers?.Origin, "https://in-stream.click");
      return { url, body: bodies[url] };
    },
  );
  assert.equal(result.url, media);
  assert.deepEqual(calls, [selected, wrapper, stream1, player, media]);
});

test("array source extraction binds the literal index and rejects unsupported data", () => {
  for (const name of ["streamUrls", "rotated_$42"]) {
    assert.equal(
      playlist(
        `const ${name} = ["https://other.test/other.m3u8", "${media}"]; player.setup({file: ${name}[1],});`,
      ),
      media,
    );
    for (const expression of [
      `${name}[2]`,
      `${name}[-1]`,
      `${name}[getIndex()]`,
      `${name}[0] + evil()`,
    ])
      assert.equal(
        playlist(
          `const ${name} = ["${media}"]; player.setup({file: ${expression},});`,
        ),
        null,
      );
  }
  for (const data of [
    '["http://insecure.test/live.m3u8"]',
    '["https://127.0.0.1/live.m3u8"]',
    '["https://cdn.test/live.mp4"]',
    "[42]",
    "[evil()]",
    '["https://cdn.test/live.m3u8", null]',
    '["https://cdn.test/live.m3u8"] + evil()',
  ])
    assert.equal(
      playlist(`const urls = ${data}; player.setup({file: urls[0],});`),
      null,
    );
  assert.equal(
    playlist(`const urls = ["${media}"]; player.setup({file: other[0],});`),
    null,
  );
});

test("malformed provider table sections do not combine separate matches", () => {
  const html = `<h2>SEPTEMBER 14, 2026</h2><table><tr><tbody>
    <tr><td class="matchtime">11:00</td><td class="event-title">First match</td><td><a href="/first/">Watch</a></td></tr>
    <tr><td class="matchtime">11:00</td><td class="event-title">Second match</td><td><a href="/second/">Watch</a></td></tr>
    </tbody></tr></table>`;
  const events = parseCatalog(html, PROVIDER_BASE, Date.UTC(2026, 8, 14));
  assert.deepEqual(events.map((e) => e.title), ["First match", "Second match"]);
  assert.ok(events.every((e) => e.startsAt === Date.UTC(2026, 8, 14, 10)));
  assert.ok(events.every((e) => e.links.length === 1));
});

test("scheduled fixtures do not leak out of the continuous-channel section", () => {
  const row = (title: string, time = "") => `<tr><td class="matchtime">${time}</td><td class="event-title">${title}</td><td><a href="/${title}/">Watch</a></td></tr>`;
  const html = `<h2>SEPTEMBER 14, 2026</h2><h2>WTA</h2><table>${row("WTA-match", "19:00")}</table>
    <h2>24/7 CHANNELS</h2><table>${row("TENNIS-CHANNEL")}</table>
    <section class="spacer"></section><div><table>${row("leftover-match", "11:00")}</table></div>
    <h2>SEPTEMBER 15, 2026</h2><h2>ATP</h2><table>${row("next-day-match", "12:00")}</table>`;
  const events = parseCatalog(html, PROVIDER_BASE);
  assert.deepEqual(events.map(e => e.title), ["WTA-match", "TENNIS-CHANNEL", "next-day-match"]);
  assert.equal(events[1].isChannel, true);
});

test("ordered evening fixtures roll past midnight within each competition table", () => {
  const row = (title: string, time: string, timestamp = "") => `<tr ${timestamp ? `data-timestamp="${timestamp}"` : ""}><td class="matchtime">${time}</td><td class="event-title">${title}</td><td><a href="/${title}/">Watch</a></td></tr>`;
  const html = `<h2>SEPTEMBER 14, 2026</h2><h2>WTA A</h2><table>
    ${row("evening", "22:00")}${row("midnight", "00:00")}${row("late", "01:30")}</table>
    <h2>WTA B</h2><table>${row("afternoon", "17:00")}${row("earlier", "16:00")}${row("explicit", "00:00", String(Date.UTC(2026, 8, 14, 20)))}</table>`;
  assert.deepEqual(parseCatalog(html, PROVIDER_BASE).map(e => e.startsAt), [
    Date.UTC(2026, 8, 14, 21), Date.UTC(2026, 8, 14, 23), Date.UTC(2026, 8, 15, 0, 30),
    Date.UTC(2026, 8, 14, 16), Date.UTC(2026, 8, 14, 15), Date.UTC(2026, 8, 14, 20),
  ]);
});

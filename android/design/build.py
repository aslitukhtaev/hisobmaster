# Builds android/design/mockup.html — the full visual design of the Android app
# (every screen, light and dark), using the website's own design tokens.
# Run: python android/design/build.py
import base64, os

HERE = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(HERE, '..', '..', 'public', 'assets', 'img')


def b64(name):
    with open(os.path.join(IMG, name), 'rb') as f:
        return 'data:image/png;base64,' + base64.b64encode(f.read()).decode()


LOGO_D = b64('logo-light.png')   # light-coloured logo for the dark theme
LOGO_L = b64('logo-dark.png')    # dark-coloured logo for the light theme

CSS = r"""
html[data-theme=light],.item[data-theme=light]{--primary:#a9fa06;--on-primary:#0e1a02;--ink:#3a7300;--tint:#eefbd4;--bg:#f4f6f1;--s1:#fff;--s2:#eef1ea;--bd:#e3e7dc;--tx:#0e0f0c;--mu:#5f675a;--red:#dc2626;--red-bg:#fef2f2;--amb:#b45309;--amb-bg:#fffbeb;--grn:#16a34a;--grn-bg:#f0fdf4;--sh:0 1px 2px rgba(14,15,12,.05),0 8px 24px -8px rgba(14,15,12,.12);--page:#dfe3d8;--glow:rgba(120,190,0,.32)}
html[data-theme=dark],.item[data-theme=dark]{--primary:#a9fa06;--on-primary:#0e1a02;--ink:#bdf94a;--tint:rgba(169,250,6,.12);--bg:#090c07;--s1:#11150e;--s2:#171c13;--bd:#232a1d;--tx:#eef3e6;--mu:#93a089;--red:#f87171;--red-bg:rgba(248,113,113,.14);--amb:#fbbf24;--amb-bg:rgba(251,191,36,.14);--grn:#4ade80;--grn-bg:rgba(74,222,128,.14);--sh:0 1px 2px rgba(0,0,0,.3),0 8px 24px -8px rgba(0,0,0,.55);--page:#050703;--glow:rgba(169,250,6,.32)}
*{box-sizing:border-box;margin:0}
body{background:var(--page);color:var(--tx);font-family:'Manrope',system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.bar{position:sticky;top:0;z-index:50;background:var(--s1);border-bottom:1px solid var(--bd);padding:10px 18px;display:flex;flex-wrap:wrap;gap:10px 18px;align-items:center;justify-content:space-between}
.bar h1{font:800 18px 'Onest','Manrope',sans-serif;letter-spacing:-.02em}
.tg{display:inline-flex;border:1px solid var(--bd);border-radius:999px;padding:3px;background:var(--bg)}
.tg button{border:0;background:none;color:var(--mu);font:700 13px 'Manrope';padding:7px 15px;border-radius:999px;cursor:pointer}
.tg button.on{background:var(--primary);color:var(--on-primary)}
.wrap{max-width:1240px;margin:0 auto;padding:22px 14px 70px}
.intro{color:var(--mu);font-size:14px;line-height:1.6;max-width:760px;margin-bottom:8px}
h3.grp{font:800 20px 'Onest','Manrope',sans-serif;letter-spacing:-.02em;margin:38px 0 4px}
p.gn{color:var(--mu);font-size:13.5px;margin-bottom:16px}
.row{display:flex;flex-wrap:wrap;gap:26px 26px;align-items:flex-start}
.item{width:336px;background:none}
.item.pair{display:contents}
.cap{font-weight:800;font-size:13px;margin-top:10px;color:var(--tx)}
.cn{font-weight:600;font-size:12.5px;color:var(--mu);margin-top:2px;line-height:1.45}
.phone{width:336px;height:690px;border-radius:36px;background:var(--bg);color:var(--tx);border:7px solid #1b1f17;position:relative;overflow:hidden;display:flex;flex-direction:column;box-shadow:0 28px 56px -22px rgba(0,0,0,.55);font-size:14px}
.sb{height:28px;flex:none;display:flex;justify-content:space-between;align-items:center;padding:0 20px;font:700 11px 'Manrope'}
.scr{flex:1;min-height:0;display:flex;flex-direction:column;overflow:hidden;position:relative}
svg.i{width:20px;height:20px;fill:none;stroke:currentColor;stroke-width:2;stroke-linecap:round;stroke-linejoin:round;flex:none}
svg.s{width:16px;height:16px}svg.l{width:24px;height:24px}
.num{font-variant-numeric:tabular-nums}
.m{font:800 16px 'Onest','Manrope',sans-serif;letter-spacing:-.01em;white-space:nowrap}
.m i,.big i{font:600 11px 'Manrope';color:var(--mu);margin-left:3px;letter-spacing:0;font-style:normal}
.big{font:800 24px 'Onest','Manrope',sans-serif;letter-spacing:-.02em}
.hd{display:flex;align-items:center;gap:8px;padding:4px 14px 8px;min-height:52px}
.hd h2{flex:1;font:800 20px 'Onest','Manrope',sans-serif;letter-spacing:-.02em;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.hd .a{display:flex;gap:8px}
.ib{width:40px;height:40px;border-radius:12px;background:var(--s1);border:1px solid var(--bd);display:grid;place-items:center;color:var(--mu);flex:none}
.ib.p{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.search{margin:0 14px;display:flex;align-items:center;gap:10px;background:var(--s1);border:1px solid var(--bd);border-radius:14px;padding:0 6px 0 14px;height:48px;color:var(--mu)}
.search.f{border:1.5px solid var(--primary);box-shadow:0 0 0 4px var(--glow)}
.search span{flex:1;font-size:14.5px;font-weight:500}.search b{flex:1;font-size:14.5px;font-weight:600;color:var(--tx)}
.scan{width:38px;height:38px;border-radius:11px;background:var(--primary);color:var(--on-primary);display:grid;place-items:center}
.scan.g{background:var(--s2);color:var(--mu)}
.fl{display:flex;gap:6px;padding:10px 14px 0;overflow:hidden}
.fl span{flex:none;padding:7px 9px;border-radius:999px;background:var(--s1);border:1px solid var(--bd);font-weight:700;font-size:12px;color:var(--mu);white-space:nowrap}
.fl .on{background:var(--primary);color:var(--on-primary);border-color:var(--primary)}
.list{padding:10px 14px 0;display:flex;flex-direction:column;gap:8px}
.p{display:flex;align-items:center;justify-content:space-between;gap:10px;background:var(--s1);border-radius:16px;box-shadow:var(--sh);padding:0 14px;min-height:58px;border:1.5px solid transparent}
.p b{font-weight:700;font-size:15px;display:block;line-height:1.3}
.p small,small.s{color:var(--mu);font-size:12px;font-weight:600}
.p.sel{border-color:var(--primary);background:var(--tint);min-height:64px}
.p.warn{border-color:var(--red)}
.step{display:flex;align-items:center;gap:2px}
.step button{width:34px;height:34px;border-radius:10px;border:0;background:var(--s1);color:var(--tx);display:grid;place-items:center;box-shadow:inset 0 0 0 1px var(--bd)}
.step button.pl{background:var(--primary);color:var(--on-primary);box-shadow:none}
.step b{min-width:30px;text-align:center;font:800 16px 'Onest','Manrope'}
.tag{display:inline-block;padding:2px 8px;border-radius:999px;font:700 11.5px 'Manrope'}
.tag.lo{background:var(--amb-bg);color:var(--amb)}.tag.ok{background:var(--tint);color:var(--ink)}.tag.rd{background:var(--red-bg);color:var(--red)}.tag.gn{background:var(--grn-bg);color:var(--grn)}.tag.nt{background:var(--s2);color:var(--mu)}
.cart{margin-top:auto;background:var(--s1);border-top:1px solid var(--bd);padding:10px 14px;display:flex;align-items:center;gap:12px}
.cart .t{flex:1}.cart small{color:var(--mu);font-size:12px;font-weight:600}
.cart b{display:block;font:800 21px 'Onest','Manrope';letter-spacing:-.02em;line-height:1.15}
.cart b i{font:600 12px 'Manrope';color:var(--mu);margin-left:3px;font-style:normal}
.cta{display:inline-flex;align-items:center;justify-content:center;gap:8px;border:0;background:var(--primary);color:var(--on-primary);font:800 16px 'Manrope';border-radius:15px;padding:0 20px;height:52px;box-shadow:0 10px 28px -10px var(--glow)}
.cta.bk{width:100%}.cta.alt{background:var(--s2);color:var(--tx);box-shadow:none;border:1px solid var(--bd)}
.cta.dg{background:var(--red-bg);color:var(--red);box-shadow:none;border:1px solid var(--red)}
.btns{display:flex;gap:8px}.btns .cta{flex:1;height:46px;font-size:14px;padding:0 10px}
.foot{margin-top:auto;padding:10px 14px 14px;background:var(--bg);border-top:1px solid var(--bd)}
.nav{height:60px;flex:none;background:var(--s1);border-top:1px solid var(--bd);display:flex}
.nav div{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:3px;font-size:10px;letter-spacing:-.1px;font-weight:700;color:var(--mu);white-space:nowrap}
.nav .on{color:var(--ink)}.nav .on svg{filter:drop-shadow(0 0 6px var(--glow))}
.lg{height:44px;width:auto;display:none}
.item[data-theme=dark] .lg-d{display:inline-block}.item[data-theme=light] .lg-l{display:inline-block}
.seg{display:grid;grid-auto-flow:column;grid-auto-columns:1fr;background:var(--s1);border:1px solid var(--bd);border-radius:14px;padding:4px;gap:4px}
.seg div{height:40px;border-radius:10px;display:grid;place-items:center;font-weight:700;font-size:13.5px;color:var(--mu)}
.seg .on{background:var(--primary);color:var(--on-primary)}
.fld{margin-top:12px}.fld label{display:block;font-size:12.5px;font-weight:700;color:var(--mu);margin-bottom:6px}
.fld div{height:50px;border-radius:14px;background:var(--s1);border:1px solid var(--bd);padding:0 14px;display:flex;align-items:center;justify-content:space-between;font-size:15px;font-weight:600}
.fld div.ph{color:var(--mu);font-weight:500}
.fld div.big2{font:800 22px 'Onest','Manrope';height:56px;letter-spacing:-.01em}
.fld div.foc{border:1.5px solid var(--primary);box-shadow:0 0 0 4px var(--glow)}
.fld div.err{border-color:var(--red)}
.fld small{display:block;margin-top:5px;font-size:12px;font-weight:600;color:var(--mu)}.fld small.e{color:var(--red)}
.pad{padding:0 14px}
.ov{position:absolute;inset:0;background:rgba(0,0,0,.6);display:flex;align-items:flex-end;z-index:5}
.sheet{background:var(--s1);width:100%;border-radius:24px 24px 0 0;padding:10px 14px 14px;max-height:100%;overflow:hidden}
.grab{width:40px;height:4px;border-radius:4px;background:var(--bd);margin:0 auto 12px}
.tot{display:flex;justify-content:space-between;align-items:baseline;margin-bottom:12px}
.tot span{font-weight:700;color:var(--mu);font-size:13px}
.tot b{font:800 32px 'Onest','Manrope';letter-spacing:-.03em}
.tot b i{font:600 13px 'Manrope';color:var(--mu);margin-left:4px;letter-spacing:0;font-style:normal}
.pay{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}
.pay div{height:50px;border-radius:14px;border:1px solid var(--bd);background:var(--s2);display:flex;align-items:center;justify-content:center;gap:7px;font-weight:700;font-size:14px;color:var(--mu)}
.pay .on{background:var(--primary);color:var(--on-primary);border-color:var(--primary);box-shadow:0 8px 22px -10px var(--glow)}
.chips{display:flex;gap:6px;margin:10px 0 0}
.chips span{flex:1;text-align:center;padding:10px 0;border-radius:12px;background:var(--s2);border:1px solid var(--bd);font-weight:700;font-size:13px}
.chips .on{border-color:var(--primary);color:var(--ink)}
.chg{display:flex;justify-content:space-between;align-items:center;background:var(--tint);border-radius:14px;padding:0 14px;height:48px;margin:10px 0;font-weight:700;font-size:14px}
.chg b{font:800 19px 'Onest','Manrope';color:var(--ink)}
.chg.rd{background:var(--red-bg)}.chg.rd b{color:var(--red)}
.two{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:12px}
.two div{height:44px;border-radius:12px;border:1px solid var(--bd);display:flex;align-items:center;justify-content:center;gap:7px;font-weight:700;font-size:13px;color:var(--mu)}
.two div.on{border-color:var(--primary);color:var(--ink);background:var(--tint)}
.hero{position:relative;overflow:hidden;isolation:isolate;margin:0 14px 10px;padding:16px;border-radius:20px;background:#0f130c;color:#f4f6f1}
.hero:before{content:'';position:absolute;z-index:-1;right:-70px;top:-110px;width:300px;height:300px;border-radius:50%;background:radial-gradient(circle,rgba(169,250,6,.36),rgba(169,250,6,0) 65%)}
.hero:after{content:'';position:absolute;z-index:-1;inset:0;background-image:radial-gradient(rgba(255,255,255,.07) 1px,transparent 1px);background-size:18px 18px;-webkit-mask-image:linear-gradient(90deg,transparent 30%,#000);mask-image:linear-gradient(90deg,transparent 30%,#000)}
.hero small{font:600 11.5px 'Geist Mono',monospace;color:var(--primary)}
.hero h3{font:800 22px 'Onest','Manrope';margin:4px 0 2px;color:#fff;letter-spacing:-.02em}
.hero p{color:rgba(244,246,241,.7);font-size:13px;margin-bottom:12px}
.hero .cta{height:48px;width:100%;justify-content:space-between;padding:0 16px;font-size:15px}
.kp{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:0 14px}
.k{position:relative;overflow:hidden;background:var(--s1);border-radius:16px;box-shadow:var(--sh);padding:11px 12px;display:grid;grid-template-columns:36px 1fr;column-gap:10px;align-items:center}
.k:after{content:'';position:absolute;right:-24px;bottom:-24px;width:80px;height:80px;border-radius:50%;background:var(--ts);opacity:.7}
.ki{position:relative;z-index:1;grid-row:1/3;width:36px;height:36px;border-radius:11px;background:var(--ts);color:var(--tn);display:grid;place-items:center}
.k small{position:relative;z-index:1;color:var(--mu);font-size:11.5px;font-weight:600}
.k b i{font:600 10.5px Manrope;color:var(--mu);margin-left:2px;font-style:normal}.k b{position:relative;z-index:1;font:800 15px 'Onest','Manrope';letter-spacing:-.01em;white-space:nowrap}
.item[data-theme=dark] .tl{--tn:#a9fa06;--ts:rgba(169,250,6,.13)}.item[data-theme=dark] .tb{--tn:#7fb0ff;--ts:rgba(127,176,255,.14)}.item[data-theme=dark] .tr{--tn:#ff8a8a;--ts:rgba(255,138,138,.14)}.item[data-theme=dark] .tv{--tn:#b5a4ff;--ts:rgba(181,164,255,.14)}.item[data-theme=dark] .ta{--tn:#ffc35c;--ts:rgba(255,195,92,.14)}.item[data-theme=dark] .tt{--tn:#5fe0c5;--ts:rgba(95,224,197,.14)}
.item[data-theme=light] .tl{--tn:#3a7300;--ts:#eefbd4}.item[data-theme=light] .tb{--tn:#2a63c7;--ts:#e3edfc}.item[data-theme=light] .tr{--tn:#d23b3b;--ts:#fde8e8}.item[data-theme=light] .tv{--tn:#6a4fd8;--ts:#ece8fd}.item[data-theme=light] .ta{--tn:#b06a00;--ts:#fdf1dc}.item[data-theme=light] .tt{--tn:#0e8f78;--ts:#dcf5ef}
.alert{margin:10px 14px 0;display:flex;align-items:center;gap:10px;padding:0 12px;height:50px;border-radius:14px;background:var(--amb-bg);border:1px solid var(--amb);color:var(--amb)}
.alert span{flex:1;font-size:13px;font-weight:700;color:var(--tx)}
.alert.rd{background:var(--red-bg);border-color:var(--red);color:var(--red)}
.alert.gn{background:var(--grn-bg);border-color:var(--grn);color:var(--grn)}
.alert.tall{height:auto;padding:12px;align-items:flex-start}
.sec{display:flex;justify-content:space-between;align-items:center;padding:14px 16px 0;font:800 14px 'Onest','Manrope'}
.sec a{font:700 12.5px 'Manrope';color:var(--ink)}
.dot{width:8px;height:8px;border-radius:50%;background:var(--primary);box-shadow:0 0 8px var(--glow)}
.dot.r{background:var(--red);box-shadow:none}.dot.a{background:var(--amb);box-shadow:none}
.card{margin:0 14px;background:var(--s1);border-radius:18px;box-shadow:var(--sh);padding:14px}
.card.t{background:var(--tint);box-shadow:none}.card.r{background:var(--red-bg);box-shadow:none;border:1px solid var(--red)}
.kv{display:flex;justify-content:space-between;align-items:center;min-height:34px;font-size:14px;font-weight:600}
.kv span{color:var(--mu)}.kv+.kv{border-top:1px solid var(--bd)}
.av{width:38px;height:38px;border-radius:50%;background:var(--tint);color:var(--ink);display:grid;place-items:center;font:800 13px 'Manrope';flex:none}
.av.lg2{width:72px;height:72px;font-size:24px}
.cl{display:flex;align-items:center;gap:11px}
.fab{position:absolute;right:16px;bottom:76px;width:54px;height:54px;border-radius:17px;background:var(--primary);color:var(--on-primary);display:grid;place-items:center;box-shadow:0 10px 28px -10px var(--glow);z-index:2}
.fab svg{width:24px;height:24px;stroke-width:2.4}
.mu{margin-top:auto}
.tab2{display:flex;gap:6px;padding:0 14px}
.sw{width:46px;height:28px;border-radius:999px;background:var(--bd);position:relative;flex:none}
.sw:after{content:'';position:absolute;left:3px;top:3px;width:22px;height:22px;border-radius:50%;background:#fff;box-shadow:0 1px 3px rgba(0,0,0,.3)}
.sw.on{background:var(--primary)}.sw.on:after{left:21px;background:var(--on-primary)}
.otp{display:flex;gap:8px;justify-content:center}.otp div{width:44px;height:56px;border-radius:12px;background:var(--s1);border:1px solid var(--bd);display:grid;place-items:center;font:800 24px 'Onest','Manrope'}
.otp .f{border:1.5px solid var(--primary);box-shadow:0 0 0 4px var(--glow)}
.prog{height:8px;border-radius:99px;background:var(--bd);overflow:hidden}.prog i{display:block;height:100%;width:62%;border-radius:99px;background:var(--primary);box-shadow:0 0 10px var(--glow)}
.paper{margin:4px 14px 0;background:#fff;color:#111;border-radius:6px;padding:16px 14px;font:500 12.5px 'Geist Mono',monospace;box-shadow:var(--sh);line-height:1.55}
.paper hr{border:0;border-top:1px dashed #999;margin:8px 0}.paper .r{display:flex;justify-content:space-between}.paper .c{text-align:center}.paper b{font-weight:600}
.led{display:flex;align-items:center;gap:10px;padding:0 14px;min-height:54px;background:var(--s1);border-radius:14px;box-shadow:var(--sh)}
.led div{flex:1}.led b{display:block;font-size:14px;font-weight:700}.led small{color:var(--mu);font-size:12px;font-weight:600}
.ic{width:34px;height:34px;border-radius:11px;display:grid;place-items:center;flex:none}
.ic.up{background:var(--red-bg);color:var(--red)}.ic.dn{background:var(--grn-bg);color:var(--grn)}.ic.nt{background:var(--s2);color:var(--mu)}
.mt{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;padding:12px 14px 0}
.mt div{background:var(--s1);border-radius:16px;box-shadow:var(--sh);padding:14px 6px 12px;display:flex;flex-direction:column;align-items:center;gap:8px;font-size:12px;font-weight:700;text-align:center}
.mt .ki{grid-row:auto}
.set{margin:0 14px;background:var(--s1);border-radius:18px;box-shadow:var(--sh);overflow:hidden}
.set>div{display:flex;align-items:center;gap:12px;min-height:54px;padding:0 14px;font-weight:700;font-size:14.5px}.set>div+div{border-top:1px solid var(--bd)}
.set>div>span{flex:1}.set small{color:var(--mu);font-size:12.5px;font-weight:600}
.hb{display:flex;align-items:center;gap:10px;padding:5px 0;font-size:12.5px;font-weight:700}.hb span{width:92px;color:var(--mu);font-weight:600}.hb i{display:block;height:10px;border-radius:99px;background:var(--primary);flex:none}.hb b{white-space:nowrap}
.tbl{display:grid;grid-template-columns:1.4fr .8fr .8fr;gap:6px;padding:0 14px}
.tbl .h{font-size:11.5px;color:var(--mu);font-weight:700;padding:4px 2px}
.tbl .c{height:44px;border-radius:11px;background:var(--s1);border:1px solid var(--bd);padding:0 10px;display:flex;align-items:center;font-size:13.5px;font-weight:600}
.tbl .c.e{color:var(--mu);font-weight:500}.tbl .c.x{border-color:var(--red)}
.cam{position:absolute;inset:0;background:#0b0d09;display:grid;place-items:center}
.cam .fr{width:230px;height:150px;border-radius:18px;border:2px solid var(--primary);box-shadow:0 0 0 999px rgba(0,0,0,.55),0 0 24px var(--glow);position:relative}
.cam .fr:after{content:'';position:absolute;left:10px;right:10px;top:50%;height:2px;background:var(--primary);box-shadow:0 0 12px var(--primary)}
.sw-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:10px}.sw-grid div{height:64px;border-radius:14px;border:1px solid var(--bd);display:flex;align-items:flex-end;padding:6px 8px;font:700 11px 'Geist Mono',monospace}
"""

SPRITE = """<svg width="0" height="0" style="position:absolute" aria-hidden="true"><defs>
<symbol id="home" viewBox="0 0 24 24"><path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"/></symbol>
<symbol id="till" viewBox="0 0 24 24"><rect x="5" y="3" width="14" height="5" rx="1"/><path d="M3 21v-7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v7z M8 16h2 M12 16h2 M16 16h.01"/></symbol>
<symbol id="receipt" viewBox="0 0 24 24"><path d="M6 3h12v18l-3-2-3 2-3-2-3 2z M9 8h6 M9 12h6"/></symbol>
<symbol id="box" viewBox="0 0 24 24"><path d="M21 8 12 3 3 8v8l9 5 9-5z M3 8l9 5 9-5 M12 13v8"/></symbol>
<symbol id="users" viewBox="0 0 24 24"><circle cx="9" cy="8" r="3.5"/><path d="M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6"/><circle cx="17" cy="9" r="2.5"/><path d="M17 14c2.5 0 4 1.8 4 4"/></symbol>
<symbol id="more" viewBox="0 0 24 24"><circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/></symbol>
<symbol id="search" viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></symbol>
<symbol id="scan" viewBox="0 0 24 24"><path d="M4 8V5a1 1 0 0 1 1-1h3 M16 4h3a1 1 0 0 1 1 1v3 M20 16v3a1 1 0 0 1-1 1h-3 M8 20H5a1 1 0 0 1-1-1v-3 M7 12h10"/></symbol>
<symbol id="plus" viewBox="0 0 24 24"><path d="M12 5v14 M5 12h14"/></symbol>
<symbol id="minus" viewBox="0 0 24 24"><path d="M5 12h14"/></symbol>
<symbol id="wallet" viewBox="0 0 24 24"><path d="M4 7h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h12 M16 13h2"/></symbol>
<symbol id="arrow" viewBox="0 0 24 24"><path d="M5 12h14 M13 6l6 6-6 6"/></symbol>
<symbol id="left" viewBox="0 0 24 24"><path d="M19 12H5 M11 6l-6 6 6 6"/></symbol>
<symbol id="chev" viewBox="0 0 24 24"><path d="M9 6l6 6-6 6"/></symbol>
<symbol id="card" viewBox="0 0 24 24"><rect x="3" y="6" width="18" height="13" rx="2"/><path d="M3 10h18"/></symbol>
<symbol id="cash" viewBox="0 0 24 24"><rect x="3" y="7" width="18" height="11" rx="2"/><circle cx="12" cy="12.5" r="2.5"/></symbol>
<symbol id="book" viewBox="0 0 24 24"><path d="M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2z M4 5v16"/></symbol>
<symbol id="alert" viewBox="0 0 24 24"><path d="M12 4 2.5 20h19z M12 10v4 M12 17h.01"/></symbol>
<symbol id="user" viewBox="0 0 24 24"><circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/></symbol>
<symbol id="pct" viewBox="0 0 24 24"><path d="M19 5 5 19"/><circle cx="7" cy="7" r="2"/><circle cx="17" cy="17" r="2"/></symbol>
<symbol id="listplus" viewBox="0 0 24 24"><path d="M4 6h12 M4 12h8 M4 18h8 M17 15v6 M14 18h6"/></symbol>
<symbol id="inbox" viewBox="0 0 24 24"><path d="M4 13v6h16v-6 M4 13l3-8h10l3 8 M9 13a3 3 0 0 0 6 0"/></symbol>
<symbol id="x" viewBox="0 0 24 24"><path d="M6 6l12 12 M18 6 6 18"/></symbol>
<symbol id="check" viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5"/></symbol>
<symbol id="phone" viewBox="0 0 24 24"><path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/></symbol>
<symbol id="sms" viewBox="0 0 24 24"><path d="M4 5h16v11H9l-5 4z M8 10h8"/></symbol>
<symbol id="edit" viewBox="0 0 24 24"><path d="M4 20h4L19 9l-4-4L4 16z M13.5 6.5l4 4"/></symbol>
<symbol id="trash" viewBox="0 0 24 24"><path d="M4 7h16 M9 7V4h6v3 M6 7l1 13h10l1-13"/></symbol>
<symbol id="cal" viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18 M8 3v4 M16 3v4"/></symbol>
<symbol id="printer" viewBox="0 0 24 24"><path d="M7 9V3h10v6 M6 17H4v-6a1 1 0 0 1 1-1h14a1 1 0 0 1 1 1v6h-2 M7 14h10v7H7z"/></symbol>
<symbol id="cloud" viewBox="0 0 24 24"><path d="M7 18a4 4 0 0 1-.5-8A6 6 0 0 1 18 9a4.5 4.5 0 0 1 0 9z"/></symbol>
<symbol id="cloudoff" viewBox="0 0 24 24"><path d="M3 3l18 18 M9 5.5A6 6 0 0 1 18 9a4.5 4.5 0 0 1 3 7.8 M6.5 10A4 4 0 0 0 7 18h10"/></symbol>
<symbol id="lock" viewBox="0 0 24 24"><rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></symbol>
<symbol id="clock" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></symbol>
<symbol id="chart" viewBox="0 0 24 24"><path d="M5 20V10 M12 20V4 M19 20v-7"/></symbol>
<symbol id="settings" viewBox="0 0 24 24"><path d="M4 7h10 M18 7h2 M4 17h2 M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/></symbol>
<symbol id="globe" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M3 12h18 M12 3a14 14 0 0 1 0 18 M12 3a14 14 0 0 0 0 18"/></symbol>
<symbol id="logout" viewBox="0 0 24 24"><path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4 M15 8l4 4-4 4 M19 12H9"/></symbol>
<symbol id="truck" viewBox="0 0 24 24"><path d="M3 6h11v10H3z M14 10h4l3 3v3h-7"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="18" r="2"/></symbol>
<symbol id="grid" viewBox="0 0 24 24"><rect x="4" y="4" width="7" height="7" rx="1.5"/><rect x="13" y="4" width="7" height="7" rx="1.5"/><rect x="4" y="13" width="7" height="7" rx="1.5"/><rect x="13" y="13" width="7" height="7" rx="1.5"/></symbol>
<symbol id="refresh" viewBox="0 0 24 24"><path d="M20 11a8 8 0 0 0-14-4 M4 4v4h4 M4 13a8 8 0 0 0 14 4 M20 20v-4h-4"/></symbol>
<symbol id="share" viewBox="0 0 24 24"><path d="M12 3v12 M8 7l4-4 4 4 M5 12v7a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-7"/></symbol>
<symbol id="info" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 11v5 M12 8h.01"/></symbol>
<symbol id="undo" viewBox="0 0 24 24"><path d="M9 14 4 9l5-5 M4 9h10a6 6 0 0 1 0 12h-3"/></symbol>
<symbol id="flash" viewBox="0 0 24 24"><path d="M13 3 5 13h6l-1 8 8-10h-6z"/></symbol>
<symbol id="eye" viewBox="0 0 24 24"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/></symbol>
<symbol id="bt" viewBox="0 0 24 24"><path d="M7 7l10 10-5 4V3l5 4L7 17"/></symbol>
</defs></svg>"""


def ic(n, cls=''):
    return f'<svg class="i {cls}"><use href="#{n}"/></svg>'


def money(v, big=False):
    return f'<span class="{"big" if big else "m"} num">{v}<i>so\'m</i></span>'


def status():
    return '<div class="sb"><span>9:41</span><span>100%</span></div>'


def hd(title, back=False, acts=''):
    b = f'<span class="ib">{ic("left")}</span>' if back else ''
    a = f'<div class="a">{acts}</div>' if acts else ''
    return f'<div class="hd">{b}<h2>{title}</h2>{a}</div>'


def ib(n, primary=False):
    return f'<span class="ib{" p" if primary else ""}">{ic(n)}</span>'


def search(ph, focus=False, scan=True, value=None, scan_green=True):
    txt = f'<b>{value}</b>' if value else f'<span>{ph}</span>'
    sc = f'<div class="scan{"" if scan_green else " g"}">{ic("scan")}</div>' if scan else ''
    return f'<div class="search{" f" if focus else ""}">{ic("search")}{txt}{sc}</div>'


def chips(items, on=0):
    return '<div class="fl">' + ''.join(f'<span class="{"on" if i == on else ""}">{t}</span>' for i, t in enumerate(items)) + '</div>'


def nav(active):
    items = [('home', 'Bosh'), ('till', 'Kassa'), ('receipt', 'Sotuvlar'), ('box', 'Mahsulotlar'), ('users', 'Qarzdorlar')]
    return '<div class="nav">' + ''.join(f'<div class="{"on" if i == active else ""}">{ic(n)}{t}</div>' for i, (n, t) in enumerate(items)) + '</div>'


def phone(num, name, note, body, nav_html='', overlay=''):
    return (f'<div class="item"><div class="phone">{status()}<div class="scr">{body}{overlay}</div>{nav_html}</div>'
            f'<div class="cap">{num}. {name}</div><div class="cn">{note}</div></div>')


def prow(name, meta, right, cls=''):
    return f'<div class="p {cls}"><div><b>{name}</b>{meta}</div><div style="text-align:right">{right}</div></div>'


def logo(h=44):
    return (f'<img class="lg lg-d" style="height:{h}px" alt="KassirON">'
            f'<img class="lg lg-l" style="height:{h}px" alt="KassirON">')


def group(title, note, items):
    return f'<h3 class="grp">{title}</h3><p class="gn">{note}</p><div class="row">{"".join(items)}</div>'


def stepper(q):
    return (f'<div class="step"><button>{ic("minus","s")}</button><b>{q}</b><button class="pl">{ic("plus","s")}</button></div>')


# ----------------------------------------------------------------------------------------------------
# Screens
# ----------------------------------------------------------------------------------------------------
S = []

# A. Boshlash ---------------------------------------------------------------------------------------
A = []
A.append(phone(1, 'Ulash', 'Birinchi ochilish. Faqat login va parol: telefon nomi o‘zi aniqlanadi.', f'''
<div class="pad" style="padding:0 18px"><div style="text-align:center;margin-top:60px">{logo(50)}</div>
<div class="seg" style="margin-top:36px"><div class="on">Login va parol</div><div>Kod bilan</div></div>
<div class="fld"><label>Login</label><div class="foc">ali</div></div>
<div class="fld"><label>Parol</label><div>••••••••<span style="color:var(--mu)">{ic("eye","s")}</span></div></div>
<button class="cta bk" style="margin-top:22px;height:54px">Ulash{ic("arrow")}</button>
<p style="text-align:center;color:var(--mu);font-size:12.5px;margin-top:14px;font-weight:600">Bir marta ulanasiz — keyin internetsiz ham ishlaydi</p></div>'''))
A.append(phone(2, 'Kod bilan ulash', 'Google/Telegram bilan kirgan egalar uchun. Kod saytda: Kompyuterlar → Telefonni ulash.', f'''
<div class="pad" style="padding:0 18px"><div style="text-align:center;margin-top:60px">{logo(50)}</div>
<div class="seg" style="margin-top:36px"><div>Login va parol</div><div class="on">Kod bilan</div></div>
<p style="color:var(--mu);font-size:13px;font-weight:600;margin:20px 0 14px;text-align:center;line-height:1.5">Saytda <b style="color:var(--tx)">Kompyuterlar → Telefonni ulash</b> bo‘limidan 6 xonali kod oling</p>
<div class="otp"><div>7</div><div>2</div><div>2</div><div class="f">2</div><div></div><div></div></div>
<button class="cta bk" style="margin-top:24px;height:54px">Ulash{ic("arrow")}</button></div>'''))
A.append(phone(3, 'Yuklanmoqda', 'Birinchi marta do‘kon ma’lumotlari yuklanadi (bir necha soniya). Keyin hammasi telefonda.', f'''
<div class="pad" style="padding:0 18px;text-align:center"><div style="margin-top:110px">{logo(44)}</div>
<div class="big" style="margin-top:34px">Do‘kon yuklanmoqda</div><p style="color:var(--mu);font-size:13.5px;font-weight:600;margin:6px 0 20px">Baraka market</p>
<div class="prog"><i></i></div>
<p class="num" style="color:var(--mu);font-size:13px;font-weight:700;margin-top:10px">1 240 ta yozuv</p>
<div class="card" style="margin:26px 0 0;text-align:left"><div class="kv"><span>Mahsulotlar</span>{ic("check","s")}</div><div class="kv"><span>Mijozlar</span>{ic("check","s")}</div><div class="kv"><span>Sotuvlar</span><b class="tag ok">yuklanmoqda</b></div></div></div>'''))
A.append(phone(4, 'Kim ishlaydi?', 'Do‘konning xodimlari. Bir telefonda bir nechta kassir ishlashi mumkin.', f'''
<div style="padding:18px 18px 0"><div style="font-size:13px;font-weight:700;color:var(--ink)">Baraka market</div><div class="big" style="font-size:28px;margin-top:2px">Kim ishlaydi?</div><p style="color:var(--mu);font-size:13.5px;font-weight:600;margin-top:2px">Ismingizni tanlang</p></div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px;padding:18px 18px 0">
<div class="card" style="margin:0;text-align:center;padding:18px 8px"><span class="av lg2" style="margin:0 auto">AV</span><b style="display:block;margin-top:10px;font-size:15px">Ali Valiyev</b><small class="s">Ega</small></div>
<div class="card" style="margin:0;text-align:center;padding:18px 8px"><span class="av lg2" style="margin:0 auto;background:var(--s2);color:var(--mu)">SK</span><b style="display:block;margin-top:10px;font-size:15px">Sardor K.</b><small class="s">Kassir</small></div></div>'''))
A.append(phone(5, 'Parol', 'Parol telefonning o‘zida tekshiriladi, internet shart emas. 5 xato — 30 soniya kutish.', f'''
<div class="hd">{ib("left")}</div><div style="text-align:center;margin-top:36px"><span class="av lg2" style="margin:0 auto">AV</span><div class="big" style="margin-top:12px">Ali Valiyev</div><small class="s">Ega</small></div>
<div class="pad" style="margin-top:26px"><div class="fld" style="margin-top:0"><div class="foc">••••••<span style="color:var(--mu)">{ic("eye","s")}</span></div></div>
<button class="cta bk" style="margin-top:16px;height:54px">Kirish</button>
<p style="text-align:center;margin-top:16px;font-weight:700;font-size:13.5px;color:var(--ink)">Boshqa foydalanuvchi</p></div>'''))
S.append(group('1. Boshlash', 'Telefonni do‘konga ulash va xodimni tanlash.', A))

# B. Kassa --------------------------------------------------------------------------------------------
B = []
cart_bar = f'<div class="cart"><div class="t"><small>3 ta mahsulot</small><b class="num">19 000<i>so\'m</i></b></div><button class="cta">To‘lash{ic("arrow")}</button></div>'
B.append(phone(6, 'Kassa', 'Eng tez ekran: qidiruv doim tayyor. Bosish = qo‘shish. Tanlanganlarda − 2 + tugmalari.', f'''
<div style="padding-top:6px"></div>{search("Nomi yoki shtrix-kod", True)}
<div class="list">
<div class="p sel"><div><b>Non</b><small class="num">3 500 so'm</small></div>{stepper(2)}</div>
<div class="p sel"><div><b>Sut 1L</b><small class="num">12 000 so'm</small></div>{stepper(1)}</div>
{prow("Choy", '<span class="tag lo">2 dona qoldi</span>', money("11 000"))}
{prow("Coca-Cola 1.5L", '<small class="s">24 dona</small>', money("12 500"))}
{prow("Shakar", '<small class="s">100 kg</small>', money("14 000"))}
{prow("Un 1 kg", '<small class="s">40 dona</small>', money("9 000"))}
</div>{cart_bar}''', nav(1)))
B.append(phone(7, 'Kassa — bo‘sh', 'Savat bo‘sh bo‘lsa pastdagi panel yo‘q. Eng ko‘p sotiladigan mahsulotlar tepada.', f'''
<div style="padding-top:6px"></div>{search("Nomi yoki shtrix-kod")}
<div class="sec" style="padding-top:12px">Ko‘p sotiladigan</div>
<div class="list">{prow("Non", '<small class="s">48 dona</small>', money("3 500"))}{prow("Sut 1L", '<small class="s">29 litr</small>', money("12 000"))}{prow("Coca-Cola 1.5L", '<small class="s">24 dona</small>', money("12 500"))}{prow("Shakar", '<small class="s">100 kg</small>', money("14 000"))}{prow("Choy", '<span class="tag lo">2 dona qoldi</span>', money("11 000"))}</div>''', nav(1)))
pay_cash = f'''<div class="grab"></div><div class="tot"><span>To‘lanadi</span><b class="num">19 000<i>so'm</i></b></div>
<div class="pay"><div class="on">{ic("cash","s")}Naqd</div><div>{ic("card","s")}Karta</div><div>{ic("book","s")}Qarz</div></div>
<div class="fld" style="margin-top:10px"><label>Berilgan naqd</label><div class="big2 num">20 000</div></div>
<div class="chips"><span>Aniq</span><span class="on num">20 000</span><span class="num">50 000</span><span class="num">100 000</span></div>
<div class="chg"><span>Qaytim</span><b class="num">1 000 so'm</b></div>
<div class="two"><div>{ic("user","s")}Mijoz</div><div>{ic("pct","s")}Chegirma</div></div>
<button class="cta bk" style="height:54px">Sotish · 19 000 so'm</button>'''
dim = '<div style="padding-top:6px"></div>' + search("Nomi yoki shtrix-kod") + '<div class="list" style="opacity:.4">' + prow("Non", '<small class="s">× 2</small>', money("7 000"), 'sel') + prow("Sut 1L", '<small class="s">× 1</small>', money("12 000"), 'sel') + '</div>'
B.append(phone(8, 'To‘lov — naqd', 'Naqd standart. Chiplar: aniq summa yoki yaxlit pul. Qaytim darhol ko‘rinadi.', dim, '', f'<div class="ov"><div class="sheet">{pay_cash}</div></div>'))
pay_debt = f'''<div class="grab"></div><div class="tot"><span>To‘lanadi</span><b class="num">19 000<i>so'm</i></b></div>
<div class="pay"><div>{ic("cash","s")}Naqd</div><div>{ic("card","s")}Karta</div><div class="on">{ic("book","s")}Qarz</div></div>
<div class="fld" style="margin-top:10px"><label>Qarzga yoziladi</label><div class="big2 num">19 000</div></div>
<div class="fld"><label>Mijoz (majburiy)</label><div class="err ph">{ic("user","s")}<span style="flex:1;margin-left:10px">Mijozni tanlang</span>{ic("chev","s")}</div><small class="e">Qarzga sotish uchun mijoz kerak</small></div>
<div class="two" style="margin-top:12px"><div>{ic("user","s")}Mijoz</div><div>{ic("pct","s")}Chegirma</div></div>
<button class="cta bk" style="height:54px">Qarzga sotish</button>'''
B.append(phone(9, 'To‘lov — qarz', 'Qarz tanlanganda mijoz majburiy: qizil ramka va tushuntirish. Qo‘shimcha naqd kiritsa, aralash bo‘ladi.', dim, '', f'<div class="ov"><div class="sheet">{pay_debt}</div></div>'))
cust = f'''<div class="grab"></div><div class="hd" style="padding:0 0 8px;min-height:40px"><h2>Mijozni tanlash</h2>{ib("x")}</div>
{search("", True, False, "Vali")}
<div class="list" style="padding:10px 0 0">
<div class="p"><div class="cl"><span class="av">VA</span><div><b>Vali aka</b><small class="s num">+998 90 999 88 77</small></div></div><span class="tag rd num">qarz 100 000</span></div>
<div class="p"><div class="cl"><span class="av">VS</span><div><b>Valijon S.</b><small class="s num">+998 93 111 22 33</small></div></div><span class="tag nt">qarz yo‘q</span></div>
<div class="p" style="border:1.5px dashed var(--primary);box-shadow:none;background:var(--tint)"><div class="cl"><span class="ic" style="background:var(--primary);color:var(--on-primary)">{ic("plus","s")}</span><div><b>Yangi mijoz: «Vali»</b><small class="s">Telefon raqamini keyin qo‘shsa bo‘ladi</small></div></div></div>
</div><div style="height:30px"></div>'''
B.append(phone(10, 'Mijoz tanlash', 'Bitta maydon: ism yoki telefon bo‘yicha qidiradi. Topilmasa — shu yerning o‘zida yangi mijoz.', dim, '', f'<div class="ov"><div class="sheet">{cust}</div></div>'))
B.append(phone(11, 'Sotuv tugadi', 'Kassa darhol keyingi sotuvga tayyor. Tepada qisqa natija: qaytim, chek, chop etish.', f'''
<div style="padding-top:6px"></div>{search("Nomi yoki shtrix-kod")}
<div class="card" style="margin-top:10px;border:1.5px solid var(--grn);background:var(--grn-bg);box-shadow:none;padding:12px 12px 12px 14px"><div style="display:flex;align-items:flex-start;gap:8px"><div style="flex:1;min-width:0"><div style="font-size:12px;font-weight:700;color:var(--grn)">Sotildi № K1-000003 · 19 000 so'm</div><div class="big num" style="font-size:22px;margin-top:2px;white-space:nowrap">Qaytim: 1 000<i>so'm</i></div></div><span class="ib" style="width:34px;height:34px">{ic("x","s")}</span></div>
<div class="btns" style="margin-top:10px"><button class="cta alt" style="height:40px">{ic("printer","s")}Chop etish</button><button class="cta alt" style="height:40px">{ic("receipt","s")}Chek</button></div></div>
<div class="list">{prow("Non", '<small class="s">48 dona</small>', money("3 500"))}{prow("Sut 1L", '<small class="s">29 litr</small>', money("12 000"))}{prow("Choy", '<span class="tag lo">2 dona qoldi</span>', money("11 000"))}{prow("Coca-Cola 1.5L", '<small class="s">24 dona</small>', money("12 500"))}</div>''', nav(1)))
B.append(phone(12, 'Chek', 'Saytdagi cheka o‘xshash. Chop etish, ulashish yoki yangi sotuv. «Fiskal chek emas» yozuvi bor.', f'''
{hd("Chek", True, ib("share"))}
<div class="paper"><div class="c"><b>Baraka market</b><br>Toshkent, Chilonzor<br>+998 90 111 22 33</div><hr>
<div class="r"><span>№ K1-000003</span><span>17.11.2026 14:26</span></div><div>Kassir: Ali Valiyev</div><hr>
<div class="r"><span>Non × 2</span><span>7 000</span></div><div class="r"><span>Sut 1L × 1</span><span>12 000</span></div><hr>
<div class="r"><b>JAMI</b><b>19 000 so'm</b></div><div class="r"><span>Naqd</span><span>20 000</span></div><div class="r"><span>Qaytim</span><span>1 000</span></div><hr>
<div class="c" style="font-size:11px">Fiskal chek emas<br>Xaridingiz uchun rahmat!</div></div>
<div class="foot"><div class="btns" style="margin-bottom:8px"><button class="cta alt">{ic("printer","s")}Chop etish</button><button class="cta alt">{ic("share","s")}Ulashish</button></div><button class="cta bk">Yangi sotuv</button></div>'''))
S.append(group('2. Kassa va to‘lov', 'Asosiy ish joyi. Hamma narsa bir qo‘l bilan, bir-ikki bosishda.', B))

# C. Bosh sahifa ------------------------------------------------------------------------------------
C = []
C.append(phone(13, 'Bosh sahifa', 'Egasi uchun kunlik holat. Kartalarni bosish tegishli bo‘limga olib boradi.', f'''
<div class="hd"><div style="flex:1;display:flex;align-items:center;gap:8px">{logo(22)}</div><div style="display:flex;align-items:center;gap:6px;font-size:12.5px;font-weight:700;color:var(--mu)"><span class="dot"></span>Baraka market</div>{ib("grid")}</div>
<div class="hero"><small>17.11.2026</small><h3>Xayrli kun, Ali!</h3><p class="num">Bugun 2 ta sotuv · 30 000 so'm</p>
<button class="cta"><span style="display:flex;align-items:center;gap:10px">{ic("till")}Kassani ochish</span>{ic("arrow","s")}</button></div>
<div class="kp">
<div class="k tl"><span class="ki">{ic("wallet")}</span><small>Tushum</small><b class="num">30 000<i>so'm</i></b></div>
<div class="k tb"><span class="ki">{ic("receipt")}</span><small>Sotuvlar</small><b class="num">2 ta</b></div>
<div class="k tr"><span class="ki">{ic("book")}</span><small>Qarz</small><b class="num">150 000<i>so'm</i></b></div>
<div class="k tv"><span class="ki">{ic("box")}</span><small>Mahsulot</small><b class="num">5 ta</b></div></div>
<div class="alert">{ic("alert","s")}<span>Choy tugayapti — 2 dona</span>{ic("chev","s")}</div>
<div class="sec">So‘nggi sotuvlar<a>Hammasi</a></div>
<div class="list" style="padding-top:8px"><div class="led"><div><b>№ K1-000002</b><small>14:26 · 1 ta mahsulot</small></div>{money("11 000")}</div><div class="led"><div><b>№ K1-000001</b><small>14:21 · 2 ta mahsulot</small></div>{money("19 000")}</div></div>
<div class="mu"></div>''', nav(0)))
C.append(phone(14, 'Ko‘proq (menyu)', 'Bosh sahifadagi yuqori o‘ng tugma. Qolgan hamma bo‘limlar shu yerda, ruxsatga qarab.', f'''
{hd("Ko‘proq", True)}
<div class="card" style="display:flex;align-items:center;gap:12px"><span class="av" style="width:46px;height:46px">AV</span><div style="flex:1"><b style="font-size:15px">Ali Valiyev</b><div style="font-size:12.5px;color:var(--mu);font-weight:600">Ega · Baraka market</div></div>{ic("chev","s")}</div>
<div class="mt">
<div><span class="ki tr">{ic("wallet")}</span>Xarajatlar</div><div><span class="ki tb">{ic("chart")}</span>Hisobotlar</div><div><span class="ki tl">{ic("inbox")}</span>Kirim</div>
<div><span class="ki ta">{ic("truck")}</span>Ta’minotchilar</div><div><span class="ki tv">{ic("grid")}</span>Kategoriyalar</div><div><span class="ki tt">{ic("listplus")}</span>Tez kiritish</div>
<div><span class="ki tr">{ic("book")}</span>Eski qarzlar</div><div><span class="ki tl">{ic("clock")}</span>Davomat</div><div><span class="ki tb">{ic("printer")}</span>Printer</div>
<div><span class="ki tv">{ic("settings")}</span>Sozlamalar</div></div>'''))
S.append(group('3. Bosh sahifa va menyu', 'Egasi uchun. Xodimda (kassir) bu ekranlar ruxsatiga qarab qisqaradi.', C))

# D. Mahsulotlar -------------------------------------------------------------------------------------
D = []
D.append(phone(15, 'Mahsulotlar', 'Tez qidiruv, filtr chiplari sonlari bilan. Qoldiq rangi: yashil — yetarli, sariq — kam, qizil — tugagan.', f'''
{hd("Mahsulotlar", False, ib("listplus") + ib("inbox"))}
{search("Nomi yoki shtrix-kod", scan_green=False)}{chips(["Hammasi 5", "Kam 1", "Tugagan 0", "Nofaol"])}
<div class="list">
{prow("Choy", '<small class="s num">4780005</small>', money("11 000") + '<br><span class="tag lo">2 dona</span>')}
{prow("Coca-Cola 1.5L", '<small class="s num">4780004</small>', money("12 500") + '<br><span class="tag ok">24 dona</span>')}
{prow("Non", '<small class="s num">4780001</small>', money("3 500") + '<br><span class="tag ok">48 dona</span>')}
{prow("Shakar", '<small class="s num">4780003</small>', money("14 000") + '<br><span class="tag ok">100 kg</span>')}
</div><div class="fab">{ic("plus")}</div><div class="mu"></div>''', nav(3)))
D.append(phone(16, 'Mahsulot qo‘shish', 'Faqat kerakli maydonlar tepada. Qo‘shimcha (MXIK, QQS, variantlar) yig‘ilgan. Saqlash tugmasi doim pastda.', f'''
{hd("Yangi mahsulot", True)}
<div class="pad" style="overflow:hidden"><div class="fld" style="margin-top:2px"><label>Nomi</label><div class="foc">Un 1 kg</div></div>
<div class="fld"><label>Shtrix-kod</label><div>4780006<span class="scan" style="width:34px;height:34px">{ic("scan","s")}</span></div></div>
<div class="fld"><label>Birlik</label><div class="chips" style="margin:0;height:auto;border:0;background:none;padding:0;display:flex;justify-content:flex-start"><span class="on">dona</span><span>kg</span><span>litr</span><span>metr</span></div></div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px"><div class="fld"><label>Sotish narxi</label><div class="num">9 000</div></div><div class="fld"><label>Tannarx</label><div class="num">7 000</div></div></div>
<div style="display:grid;grid-template-columns:1fr 1fr;gap:10px"><div class="fld"><label>Boshlang‘ich qoldiq</label><div class="num">40</div></div><div class="fld"><label>Kategoriya</label><div>Oziq-ovqat{ic("chev","s")}</div></div></div>
<div class="set" style="margin:14px 0 0"><div><span>Qo‘shimcha</span><small>MXIK, QQS, variantlar</small>{ic("chev","s")}</div></div></div>
<div class="foot"><button class="cta bk">Saqlash</button></div>'''))
D.append(phone(17, 'Tez kiritish', 'Yangi do‘kon ochilganda: ko‘p mahsulotni qatorlab yozish. Enter — keyingi katak. Xato qator qizil.', f'''
{hd("Tez kiritish", True, '<span class="tag ok">3 qator</span>')}
<div class="tbl"><div class="h">Nomi</div><div class="h">Narx</div><div class="h">Soni</div>
<div class="c">Un 1 kg</div><div class="c num">9 000</div><div class="c num">40</div>
<div class="c">Yog‘ 1L</div><div class="c num">22 000</div><div class="c num">15</div>
<div class="c x">Tuz</div><div class="c x e">narx?</div><div class="c num">30</div>
<div class="c e">Nomi</div><div class="c e">0</div><div class="c e">0</div>
<div class="c e">Nomi</div><div class="c e">0</div><div class="c e">0</div></div>
<div class="alert rd" style="height:auto;padding:10px 12px"><span style="font-weight:600;font-size:12.5px">Tuz: narxni kiriting</span></div>
<div class="pad" style="margin-top:10px"><button class="cta alt bk" style="height:44px;font-size:14px">{ic("plus","s")}10 qator qo‘shish</button></div>
<div class="foot"><button class="cta bk">2 ta mahsulotni saqlash</button></div>'''))
D.append(phone(18, 'Kirim (tovar kirimi)', 'Ombor to‘ldirish. Qidirish yoki skaner bilan qo‘shiladi. Narxni yangilash — ixtiyoriy.', f'''
{hd("Yangi kirim", True)}
<div class="pad"><div class="fld" style="margin-top:2px"><label>Ta’minotchi</label><div>Bozor ulgurji{ic("chev","s")}</div></div></div>
{search("Mahsulot qo‘shish", scan=True)}
<div class="list">
<div class="p sel"><div><b>Non</b><small class="s num">tannarx 2 500</small></div>{stepper(100)}</div>
<div class="p sel"><div><b>Un 1 kg</b><small class="s num">tannarx 7 000</small></div>{stepper(40)}</div></div>
<div class="set" style="margin-top:12px"><div><span>Tannarxni yangilash</span><div class="sw on"></div></div></div>
<div class="foot"><div class="tot" style="margin-bottom:10px"><span>Jami</span><b class="num" style="font-size:24px">530 000<i>so'm</i></b></div><button class="cta bk">Kirimni saqlash</button></div>'''))
S.append(group('4. Mahsulotlar va ombor', 'Yangi mahsulot qo‘shish eng tez bo‘lishi kerak, ayniqsa yangi boshlaganda.', D))

# E. Sotuvlar ---------------------------------------------------------------------------------------
E = []
E.append(phone(19, 'Sotuvlar', 'Kunlik tushum tepada. Sana va to‘lov turi bo‘yicha filtr. Qaytarilgan sotuvlar belgilanadi.', f'''
{hd("Sotuvlar", False, ib("cal"))}
{chips(["Bugun", "Kecha", "7 kun", "Oy"])}
<div class="card" style="margin-top:10px"><div style="display:flex;justify-content:space-between;align-items:flex-start"><div><small class="s">Tushum</small><div class="big num">30 000<i>so'm</i></div></div><div style="text-align:right"><small class="s">Sotuvlar</small><div class="big">2</div></div></div>
<div style="display:flex;gap:16px;margin-top:10px;font-size:12.5px;font-weight:700"><span style="color:var(--grn)">Naqd 30 000</span><span style="color:#5b8def">Karta 0</span><span style="color:var(--red)">Qarz 0</span></div></div>
{chips(["Hammasi", "Naqd", "Karta", "Qarz", "Aralash"]).replace('class="fl"', 'class="fl" style="gap:5px"')}
<div class="list"><div class="led"><div><b>№ K1-000002 <small>14:26</small></b><small>1 ta mahsulot</small></div><div style="text-align:right">{money("11 000")}<br><span class="tag gn">Naqd</span></div></div>
<div class="led"><div><b>№ K1-000001 <small>14:21</small></b><small>2 ta mahsulot</small></div><div style="text-align:right">{money("19 000")}<br><span class="tag gn">Naqd</span></div></div></div><div class="mu"></div>''', nav(2)))
E.append(phone(20, 'Sotuv tafsiloti', 'Tarkib, to‘lovlar, qaytim. Pastda: qaytarish va chek.', f'''
{hd("№ K1-000001", True, ib("printer"))}
<div class="card" style="padding:4px 14px"><div class="kv"><span>Vaqt</span>17.11.2026 14:21</div><div class="kv"><span>Kassir</span>Ali Valiyev</div><div class="kv"><span>Mijoz</span>—</div></div>
<div class="sec">Mahsulotlar</div><div class="list" style="padding-top:8px">
<div class="led"><div><b>Non</b><small class="num">3 500 × 2</small></div>{money("7 000")}</div><div class="led"><div><b>Sut 1L</b><small class="num">12 000 × 1</small></div>{money("12 000")}</div></div>
<div class="card" style="margin-top:10px;padding:4px 14px"><div class="kv"><span>Naqd</span><b class="num">20 000</b></div><div class="kv"><span>Qaytim</span><b class="num">1 000</b></div><div class="kv"><span>Jami</span><b class="num" style="font-size:16px">19 000 so'm</b></div></div>
<div class="foot"><div class="btns"><button class="cta alt">{ic("receipt","s")}Chek</button><button class="cta dg">{ic("undo","s")}Qaytarish</button></div></div>'''))
E.append(phone(21, 'Qaytarish', 'Faqat qaytarilmagan miqdor tanlanadi. To‘lov turlari bo‘yicha avtomatik taqsimlanadi.', f'''
{hd("Qaytarish", True)}
<div class="list" style="padding-top:2px">
<div class="p sel"><div><b>Non</b><small class="s num">3 500 · sotilgan 2</small></div>{stepper(1)}</div>
<div class="p"><div><b>Sut 1L</b><small class="s num">12 000 · sotilgan 1</small></div>{stepper(0)}</div></div>
<div class="pad"><div class="fld"><label>Sabab (ixtiyoriy)</label><div class="ph">Mijoz qaytardi</div></div></div>
<div class="card t" style="margin-top:14px"><div class="kv"><span>Naqd qaytariladi</span><b class="num" style="font-size:18px;color:var(--ink)">3 500 so'm</b></div></div>
<div class="foot"><button class="cta bk dg" style="height:54px">3 500 so'm qaytarish</button></div>'''))
S.append(group('5. Sotuvlar va qaytarish', 'Tarix va xatolarni to‘g‘rilash.', E))

# F. Qarzdorlar -----------------------------------------------------------------------------------------
F = []
F.append(phone(22, 'Qarzdorlar', 'Jami qarz tepada. Qarzdorlar birinchi. Ism yoki telefon bo‘yicha qidiruv.', f'''
{hd("Qarzdorlar", False, ib("listplus"))}
<div class="card r" style="padding:12px 16px"><small class="s">Jami qarz · 2 ta qarzdor</small><div class="big num" style="color:var(--red)">150 000<i>so'm</i></div></div>
<div style="height:10px"></div>{search("Ism yoki telefon", scan=False)}{chips(["Qarzdorlar", "Hammasi"])}
<div class="list">
<div class="p"><div class="cl"><span class="av">VA</span><div><b>Vali aka</b><small class="s num">+998 90 999 88 77</small></div></div><div class="m num" style="color:var(--red)">100 000<i>so'm</i></div></div>
<div class="p"><div class="cl"><span class="av">NS</span><div><b>Nodira Saidova</b><small class="s num">+998 91 234 56 78</small></div></div><div class="m num" style="color:var(--red)">50 000<i>so'm</i></div></div></div>
<div class="fab">{ic("plus")}</div><div class="mu"></div>''', nav(4)))
F.append(phone(23, 'Qarzdor tafsiloti', 'Qo‘ng‘iroq va SMS (eslatma matni tayyor). Tarixda har bir qarz va to‘lov, qoldiq bilan.', f'''
{hd("Vali aka", True, ib("edit"))}
<div style="text-align:center;margin-top:2px"><small class="s">Qarzi</small><div class="big num" style="font-size:32px;color:var(--red)">100 000<i>so'm</i></div></div>
<div class="btns pad" style="margin-top:12px"><button class="cta alt">{ic("phone","s")}Qo‘ng‘iroq</button><button class="cta alt">{ic("sms","s")}SMS</button></div>
<div class="pad" style="margin-top:8px"><button class="cta bk" style="height:50px">To‘lov qabul qilish</button></div>
<div class="sec">Tarix</div><div class="list" style="padding-top:8px">
<div class="led"><span class="ic up">{ic("plus","s")}</span><div><b>Qarz · № K1-000004</b><small>16.11 · qoldiq 100 000</small></div><b class="num" style="color:var(--red)">+60 000</b></div>
<div class="led"><span class="ic dn">{ic("minus","s")}</span><div><b>To‘lov</b><small>12.11 · qoldiq 40 000</small></div><b class="num" style="color:var(--grn)">−20 000</b></div>
<div class="led"><span class="ic up">{ic("plus","s")}</span><div><b>Eski qarz</b><small>01.11 · qoldiq 60 000</small></div><b class="num" style="color:var(--red)">+60 000</b></div></div>'''))
payin = f'''<div class="grab"></div><div class="hd" style="padding:0 0 8px;min-height:40px"><h2>To‘lov qabul qilish</h2>{ib("x")}</div>
<div class="fld" style="margin-top:0"><label>Summa</label><div class="big2 num foc">50 000</div></div>
<div class="chips"><span>Hammasi</span><span class="num">Yarmi</span><span class="num">20 000</span></div>
<div class="chg" style="margin-top:12px"><span>Qolgan qarz</span><b class="num">50 000 so'm</b></div>
<button class="cta bk" style="height:54px;margin-top:4px">Qabul qilish</button>'''
F.append(phone(24, 'To‘lov qabul qilish', 'Summa kiritiladi, qolgan qarz darhol ko‘rinadi. Qarzdan ko‘p kiritib bo‘lmaydi.', f'''
{hd("Vali aka", True)}<div style="text-align:center"><div class="big num" style="font-size:32px;color:var(--red)">100 000<i>so'm</i></div></div>''', '', f'<div class="ov"><div class="sheet">{payin}<div style="height:90px"></div></div></div>'))
F.append(phone(25, 'Eski qarzlar (tez)', 'Daftardagi eski qarzlarni tez kiritish: ism, telefon, summa. Bir marta saqlanadi.', f'''
{hd("Eski qarzlar", True, '<span class="tag ok">2 qator</span>')}
<div class="tbl" style="grid-template-columns:1fr 1.35fr .85fr"><div class="h">Ism</div><div class="h">Telefon</div><div class="h">Summa</div>
<div class="c">Karim aka</div><div class="c num">90 123 45 67</div><div class="c num">80 000</div>
<div class="c">Dilfuza</div><div class="c e">telefon</div><div class="c num">35 000</div>
<div class="c e">Ism</div><div class="c e">telefon</div><div class="c e">0</div>
<div class="c e">Ism</div><div class="c e">telefon</div><div class="c e">0</div></div>
<div class="pad" style="margin-top:12px"><button class="cta alt bk" style="height:44px;font-size:14px">{ic("plus","s")}10 qator qo‘shish</button></div>
<div class="foot"><button class="cta bk">2 ta qarzni saqlash</button></div>'''))
S.append(group('6. Qarzdorlar', 'Do‘konlar uchun eng muhim bo‘limlardan biri.', F))

# G. Xarajatlar va hisobotlar -------------------------------------------------------------------------------
G = []
G.append(phone(26, 'Xarajatlar', 'Oy boshidan bugungacha. Jami va kategoriya bo‘yicha. Bosib tahrirlash, surib o‘chirish.', f'''
{hd("Xarajatlar", True, ib("cal"))}
<div class="card"><small class="s">Jami xarajat · noyabr</small><div class="big num">420 000<i>so'm</i></div>
<div style="margin-top:8px"><div class="hb"><span>Ijara</span><i style="width:120px"></i><b class="num">300 000</b></div><div class="hb"><span>Transport</span><i style="width:40px"></i><b class="num">70 000</b></div><div class="hb"><span>Boshqa</span><i style="width:34px"></i><b class="num">50 000</b></div></div></div>
<div class="list"><div class="led"><span class="ic nt">{ic("wallet","s")}</span><div><b>Ijara</b><small>15.11 · do‘kon ijarasi</small></div>{money("300 000")}</div>
<div class="led"><span class="ic nt">{ic("wallet","s")}</span><div><b>Transport</b><small>12.11 · yetkazib berish</small></div>{money("70 000")}</div></div>
<div class="fab" style="bottom:20px">{ic("plus")}</div>'''))
G.append(phone(27, 'Xarajat qo‘shish', 'Summa katta va birinchi. Kategoriya bir bosishda, yangisini shu yerda yozish mumkin.', f'''
{hd("Yangi xarajat", True)}
<div class="pad"><div class="fld" style="margin-top:2px"><label>Summa</label><div class="big2 num foc">70 000</div></div>
<div class="fld"><label>Kategoriya</label><div class="chips" style="margin:0;height:auto;border:0;background:none;padding:0;flex-wrap:wrap;gap:6px;justify-content:flex-start"><span class="on" style="flex:none;padding:10px 14px">Ijara</span><span style="flex:none;padding:10px 14px">Transport</span><span style="flex:none;padding:10px 14px">Maosh</span><span style="flex:none;padding:10px 14px">+ Yangi</span></div></div>
<div class="fld"><label>Izoh</label><div class="ph">Yetkazib berish</div></div>
<div class="fld"><label>Sana</label><div>17.11.2026{ic("cal","s")}</div></div></div>
<div class="foot"><button class="cta bk">Saqlash</button></div>'''))
G.append(phone(28, 'Hisobotlar', 'Davr tanlanadi. Tushum, foyda, to‘lov turlari, eng ko‘p sotilganlar. Faqat «hisobot» ruxsati bilan.', f'''
{hd("Hisobotlar", True)}
{chips(["Bugun", "Kecha", "Hafta", "Oy", "Sana"], 3)}
<div class="kp" style="margin-top:10px"><div class="k tl"><span class="ki">{ic("wallet")}</span><small>Tushum</small><b class="num">1 240 000</b></div><div class="k tt"><span class="ki">{ic("chart")}</span><small>Foyda</small><b class="num">390 000</b></div>
<div class="k tr"><span class="ki">{ic("wallet")}</span><small>Xarajat</small><b class="num">420 000</b></div><div class="k tv"><span class="ki">{ic("undo")}</span><small>Qaytarilgan</small><b class="num">12 000</b></div></div>
<div class="sec">To‘lov turlari</div><div class="card" style="margin-top:8px"><div class="hb"><span>Naqd</span><i style="width:150px"></i><b class="num">860 000</b></div><div class="hb"><span>Karta</span><i style="width:70px;background:#5b8def"></i><b class="num">280 000</b></div><div class="hb"><span>Qarz</span><i style="width:44px;background:var(--red)"></i><b class="num">100 000</b></div></div>
<div class="sec">Eng ko‘p sotilgan</div><div class="list" style="padding-top:8px"><div class="led"><div><b>Non</b><small>312 dona</small></div>{money("1 092 000")}</div><div class="led"><div><b>Sut 1L</b><small>40 litr</small></div>{money("480 000")}</div></div>'''))
S.append(group('7. Xarajatlar va hisobotlar', 'Pul qayerga ketayotganini ko‘rish.', G))

# H. Sozlamalar va holatlar ---------------------------------------------------------------------------------
H = []
H.append(phone(29, 'Sozlamalar', 'Sinxronlash holati, til, foydalanuvchini almashtirish. Telefonni uzish — tasdiq bilan.', f'''
{hd("Sozlamalar", True)}
<div class="card t" style="display:flex;align-items:center;gap:12px"><span class="ic" style="background:var(--primary);color:var(--on-primary);width:42px;height:42px">{ic("cloud")}</span><div style="flex:1"><b style="font-size:14.5px">Hammasi yuborilgan</b><div style="font-size:12.5px;color:var(--mu);font-weight:600">Oxirgi: 17.11.2026 14:27</div></div><button class="cta" style="height:38px;padding:0 14px;font-size:13px">{ic("refresh","s")}</button></div>
<div style="height:10px"></div><div class="set">
<div>{ic("globe")}<span>Til</span><div class="seg" style="width:120px;padding:3px"><div class="on" style="height:30px;font-size:12.5px">UZ</div><div style="height:30px;font-size:12.5px">RU</div></div></div>
<div>{ic("printer")}<span>Printer</span><small>XP-58</small>{ic("chev","s")}</div>
<div>{ic("user")}<span>Foydalanuvchini almashtirish</span>{ic("chev","s")}</div></div>
<div style="height:10px"></div><div class="set"><div>{ic("info")}<span>Do‘kon</span><small>Baraka market</small></div><div>{ic("phone")}<span>Qurilma</span><small>K2</small></div><div>{ic("info")}<span>Versiya</span><small>2.0.0</small></div></div>
<div class="pad" style="margin-top:14px"><button class="cta dg bk" style="height:48px;font-size:14.5px">Telefonni uzish</button></div>'''))
H.append(phone(30, 'Printer', 'Bluetooth termoprinter. Juftlangan printerlardan tanlanadi, 58/80 mm, sinov chop etish.', f'''
{hd("Printer", True)}
<div class="sec" style="padding-top:2px">Juftlangan printerlar</div>
<div class="list" style="padding-top:8px"><div class="p sel"><div class="cl"><span class="ic nt" style="background:var(--s1)">{ic("bt","s")}</span><div><b>XP-58IIH</b><small class="s num">66:22:A1:0B:4F:90</small></div></div>{ic("check")}</div>
<div class="p"><div class="cl"><span class="ic nt">{ic("bt","s")}</span><div><b>MPT-II</b><small class="s num">00:11:22:33:44:55</small></div></div></div></div>
<div class="pad" style="margin-top:14px"><div style="font-size:12.5px;font-weight:700;color:var(--mu);margin-bottom:6px">Qog‘oz kengligi</div><div class="seg"><div class="on">58 mm</div><div>80 mm</div></div></div>
<div class="foot"><button class="cta bk">{ic("printer")}Sinov chop etish</button></div>'''))
H.append(phone(31, 'Skaner', 'Kamera bilan shtrix-kod. Uzluksiz rejim: har bir kod — ovoz va tebranish, kassada savatga qo‘shiladi.', f'''
<div class="cam"><div class="fr"></div></div>
<div style="position:absolute;left:14px;right:14px;top:12px;display:flex;justify-content:space-between">{ib("x")}{ib("flash")}</div>
<div style="position:absolute;left:0;right:0;bottom:30px;text-align:center;color:#fff;font-weight:700;font-size:14px">Shtrix-kodni ramka ichiga qo‘ying</div>'''))
H.append(phone(32, 'Kassa — internet yo‘q', 'Internet bo‘lmasa ham sotadi. Kichik banner: nechta sotuv yuborilishini kutayotgani. Xalaqit bermaydi.', f'''
<div style="padding-top:6px"></div>
<div class="alert" style="margin:0 14px 10px;height:42px">{ic("cloudoff","s")}<span style="font-size:12.5px">Internet yo‘q · 3 ta sotuv yuborilmagan</span></div>
{search("Nomi yoki shtrix-kod")}
<div class="list">{prow("Non", '<small class="s">47 dona</small>', money("3 500"))}{prow("Sut 1L", '<small class="s">28 litr</small>', money("12 000"))}{prow("Choy", '<span class="tag lo">2 dona qoldi</span>', money("11 000"))}</div>''', nav(1)))
H.append(phone(33, 'Muddat tugadi', 'Sinov yoki obuna tugaganda: oddiy xabar, to‘lash yo‘li. Ma’lumotlar saqlanadi. Telefon sotishni to‘xtatadi.', f'''
<div style="text-align:center;padding:90px 22px 0"><span class="ic" style="width:68px;height:68px;border-radius:21px;margin:0 auto;background:var(--amb-bg);color:var(--amb)">{ic("clock","l")}</span>
<div class="big" style="margin-top:20px;font-size:25px">Bepul sinov tugadi</div>
<p style="color:var(--mu);font-size:14px;font-weight:600;margin-top:8px;line-height:1.55">Davom etish uchun obunani to‘lang. Barcha mahsulot, sotuv va qarz ma’lumotlaringiz saqlanadi.</p>
<button class="cta bk" style="margin-top:26px;height:54px">Telegram orqali to‘lash</button>
<button class="cta alt bk" style="margin-top:10px;height:50px">+998 99 702 67 20</button></div>'''))
S.append(group('8. Sozlamalar, printer va maxsus holatlar', 'Internet yo‘qligi, obuna tugashi va boshqa holatlar ham chiroyli va tushunarli bo‘lishi kerak.', H))

# ----------------------------------------------------------------------------------------------------
# Design system
# ----------------------------------------------------------------------------------------------------
def swatches(theme):
    names = [('--bg', 'Fon'), ('--s1', 'Karta'), ('--s2', 'Ikkinchi fon'), ('--bd', 'Chiziq'), ('--tx', 'Matn'), ('--mu', 'Xira matn'), ('--primary', 'Asosiy'), ('--tint', 'Yashil fon'), ('--red', 'Xavf'), ('--amb', 'Ogohlantirish'), ('--grn', 'Muvaffaqiyat')]
    cells = ''.join(f'<div style="background:var({v});color:{"#0e1a02" if v in ("--primary",) else "inherit"}">{t}</div>' for v, t in names)
    return f'<div class="item" data-theme="{theme}" style="width:auto"><div style="background:var(--bg);color:var(--tx);padding:14px;border-radius:18px;border:1px solid var(--bd)"><div style="font:800 13px Manrope;margin-bottom:10px">{"Kunduzgi" if theme == "light" else "Tungi"} rejim</div><div class="sw-grid">{cells}</div></div></div>'


DS = f'''<h3 class="grp" style="margin-top:6px">0. Dizayn tizimi</h3>
<p class="gn">Hammasi saytdagi dizayndan: bir xil ranglar, shriftlar (Manrope va Onest), yumaloq kartalar, yashil tugmalar va yengil yorug‘lik. Ilova telefon rejimiga qarab o‘zi kunduzgi yoki tungi bo‘ladi, sozlamada o‘zgartirib ham bo‘ladi.</p>
<div class="row" style="gap:18px;align-items:stretch">{swatches("light")}{swatches("dark")}
<div class="item" style="width:340px"><div class="card" style="margin:0"><div class="big" style="font-size:26px">Sarlavha — Onest 800</div><p style="font-size:15px;font-weight:600;margin-top:8px">Asosiy matn — Manrope 600, 15 px</p><p style="font-size:12px;color:var(--mu);font-weight:600;margin-top:4px">Yordamchi matn — 12 px, xira</p>
<div style="display:flex;gap:8px;margin-top:14px"><button class="cta" style="height:44px;font-size:14px">Asosiy</button><button class="cta alt" style="height:44px;font-size:14px">Ikkinchi</button><button class="cta dg" style="height:44px;font-size:14px">Xavf</button></div>
<div style="display:flex;gap:6px;margin-top:12px"><span class="tag ok">Yetarli</span><span class="tag lo">Kam</span><span class="tag rd">Tugagan</span><span class="tag gn">Naqd</span><span class="tag nt">Nofaol</span></div></div></div></div>
<p class="gn" style="margin-top:14px">Qoidalar: tugma balandligi 44-54 px, kartalar 16-20 px yumaloq, ekran chetidan 14 px, raqamlar doim bir xil kenglikda (ustunlar tekis), pul «19 000 so‘m» ko‘rinishida, har bir bosiladigan joy kamida 40 px.</p>'''

HTML = f'''<!doctype html>
<html lang="uz">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>KassirON Android — to‘liq dizayn</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link href="https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&family=Onest:wght@500;700;800&family=Geist+Mono:wght@500;600&display=swap" rel="stylesheet">
<style>{CSS}.lg-d{{content:url({LOGO_D})}}.lg-l{{content:url({LOGO_L})}}</style>
</head>
<body>
{SPRITE}
<div class="bar"><h1>KassirON Android — to‘liq dizayn</h1>
<div style="display:flex;gap:10px;flex-wrap:wrap;align-items:center"><div class="tg" id="tg"><button data-m="light" class="on">Kunduzgi</button><button data-m="dark">Tungi</button><button data-m="both">Ikkalasi yonma-yon</button></div></div></div>
<div class="wrap">
<p class="intro">Ilovaning barcha ekranlari. Yuqoridagi tugma bilan kunduzgi va tungi rejimni almashtiring yoki ikkalasini yonma-yon ko‘ring. Yoqsa, aynan shunday qilib chiqaman.</p>
{DS}
{"".join(S)}
</div>
<script>
var items=[].slice.call(document.querySelectorAll('.item')).filter(function(i){{return i.querySelector('.phone')}});
var originals=items.map(function(i){{return i.cloneNode(true)}});
function apply(m){{
  var btns=document.querySelectorAll('#tg button');[].forEach.call(btns,function(b){{b.className=b.dataset.m===m?'on':''}});
  document.documentElement.setAttribute('data-theme',m==='dark'?'dark':'light');
  [].forEach.call(document.querySelectorAll('.item[data-pair]'),function(n){{n.remove()}});
  items.forEach(function(it,idx){{
    var t=m==='dark'?'dark':'light';it.setAttribute('data-theme',t);
    if(m==='both'){{it.setAttribute('data-theme','light');var c=originals[idx].cloneNode(true);c.setAttribute('data-theme','dark');c.setAttribute('data-pair','1');it.parentNode.insertBefore(c,it.nextSibling)}}
  }});
}}
document.querySelectorAll('#tg button').forEach(function(b){{b.onclick=function(){{apply(b.dataset.m)}}}});
document.querySelectorAll('.item:not(:has(.phone))').forEach(function(i){{}});
apply(['light','dark','both'].indexOf(location.hash.slice(1))>=0?location.hash.slice(1):'light');
</script>
</body></html>'''

# swatch / design-system items carry their own data-theme and must not be touched by the toggler
with open(os.path.join(HERE, 'mockup.html'), 'w', encoding='utf-8') as f:
    f.write(HTML)
print('ok', len(HTML) // 1024, 'KB')

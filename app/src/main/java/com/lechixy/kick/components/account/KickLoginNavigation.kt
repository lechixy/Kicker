package com.lechixy.kick.components.account

import java.net.URI

internal const val KICK_LOGIN_URL = "https://kick.com/?auth=sign-in"

// Exact Google-owned regional domains, from https://www.google.com/supported_domains.
// Do not replace this with a google.* suffix match: that would also trust lookalikes.
private val googleAccountHosts = """
    com ad ae com.af com.ag al am co.ao com.ar as at com.au az ba com.bd be bf bg com.bh
    bi bj com.bn com.bo com.br bs bt co.bw by com.bz ca cd cf cg ch ci co.ck cl cm cn
    com.co co.cr com.cu cv com.cy cz de dj dk dm com.do dz com.ec ee com.eg es com.et
    fi com.fj fm fr ga ge gg com.gh com.gi gl gm gr com.gt gy com.hk hn hr ht hu co.id
    ie co.il im co.in iq is it je com.jm jo co.jp co.ke com.kh ki kg co.kr com.kw kz la
    com.lb li lk co.ls lt lu lv com.ly co.ma md me mg mk ml com.mm mn com.mt mu mv mw
    com.mx com.my co.mz com.na com.ng com.ni ne nl no com.np nr nu co.nz com.om com.pa
    com.pe com.pg com.ph com.pk pl pn com.pr ps pt com.py com.qa ro ru rw com.sa com.sb
    sc se com.sg sh si sk com.sl sn so sm sr st com.sv td tg co.th com.tj tl tm tn to
    com.tr tt com.tw co.tz com.ua co.ug co.uk com.uy co.uz com.vc co.ve co.vi com.vn
    vu ws rs co.za co.zm co.zw cat
""".trim().split(Regex("\\s+")).map { "accounts.google.$it" }.toSet() + "accounts.youtube.com"

internal fun isKickLoginUrl(url: String): Boolean = runCatching {
    val uri = URI(url)
    val host = uri.host.orEmpty().lowercase()
    uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
        (host == "kick.com" || host.endsWith(".kick.com"))
}.getOrDefault(false)

internal fun isAllowedLoginUrl(url: String): Boolean = isKickLoginUrl(url) || runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host.orEmpty().lowercase() in googleAccountHosts &&
        uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)

// Only run on Kick documents. OneTrust is loaded asynchronously, including after SPA routing.
internal val kickLoginPageScript = """
    (() => {
      if (window.__kickLoginConsent) return;
      window.__kickLoginConsent = true;
      const accept = () => {
        const button = document.getElementById('onetrust-accept-btn-handler');
        if (button && button.getClientRects().length && !button.disabled) {
          button.click();
          return true;
        }
        return false;
      };
      if (accept()) return;
      const observer = new MutationObserver(() => { if (accept()) observer.disconnect(); });
      observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['style','class']});
      setTimeout(() => observer.disconnect(), 30000);
    })();
""".trimIndent()

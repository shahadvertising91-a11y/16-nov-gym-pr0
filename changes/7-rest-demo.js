<style id="restdemo">
.rsv{text-align:center}
.rsv .ph{text-align:left}
.rvk{font-size:10.5px;letter-spacing:.22em;text-transform:uppercase;color:var(--mu);font-weight:700;margin-top:4px}
.rvr{position:relative;width:min(58vw,224px);aspect-ratio:1;margin:12px auto 18px}
.rvr svg{width:100%;height:100%;transform:rotate(-90deg);display:block}
.rvr circle{fill:none;stroke-width:7}
.rvt{stroke:var(--s2)}
.rvp{stroke:var(--ac);stroke-linecap:round;transition:stroke-dashoffset .12s linear}
.rvc{position:absolute;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center}
.rvc b{font-family:var(--fd);font-size:84px;line-height:.9;font-weight:700;font-variant-numeric:tabular-nums;color:var(--tx)}
.rvc span{font-size:10.5px;letter-spacing:.2em;text-transform:uppercase;color:var(--mu);font-weight:700;margin-top:6px}
.rvr.end .rvc b{color:var(--acx,var(--ac))}
.rvn{display:flex;align-items:center;gap:14px;text-align:left;padding:12px;border-radius:22px;background:var(--s1);border:1px solid var(--bd)}
.rvn .vb{width:84px;flex:none;border-radius:16px}
.rvn div{min-width:0;flex:1}
.rvn small{display:block;font-size:10px;letter-spacing:.14em;text-transform:uppercase;color:var(--acx,var(--ac));font-weight:700}
.rvn b{display:block;font-size:16px;line-height:1.25;margin:3px 0 2px;color:var(--tx)}
.rvn span{display:block;font-size:12.3px;color:var(--mu)}
.rsv .steps{text-align:left;margin-top:18px}
.rsv .steps li,.rsv .steps li span{font-size:13.3px;line-height:1.55;color:var(--tx)}
.dock>div.rvd.rvd:last-child:not(.rs){display:flex!important;gap:12px!important;margin-top:0!important}
.rvd .btn{margin:0!important;width:auto!important;min-width:0;flex:1.4 1 0;text-align:center}
.rvd .btn.gh{flex:1 1 0}
@media (prefers-reduced-motion:reduce){.rvp{transition:none}}
</style>
<script>
/* ===== 7) first-exercise demo  +  full rest screen (replaces the small rest bar in the bottom dock) ===== */
(function(){
  const DEMO=20,C=339.3;                                   /* demo seconds (same as the other exercises), ring length */
  const fn=(n,d)=>{try{return (0,eval)(n)}catch(e){return d}};
  const _wd=fn("wd",v=>v),_bwx=fn("bwx",()=>false),_pad=fn("pad",n=>String(n|0).padStart(2,"0"));

  function restV(P){
    const n=nextSet();if(!n)return null;
    const W=P.wk.ex,x=W[n[0]],e=XK[x.k],ss=P.sets[n[0]],s=ss[n[1]],isNew=ss.every(q=>!q.d),
      el=Math.floor((Date.now()-P.t0)/1000),
      load=!_bwx(x)&&s.w?" · "+_wd(s.w)+" "+S.unit:"",
      what=(x.t?s.r+" sec":s.r+" reps")+load,
      lab=isNew?"Up next · Exercise "+(n[0]+1)+" of "+W.length:"Next · Set "+(n[1]+1)+" of "+ss.length;
    P.rmax=Math.max(P.rmax||0,P.rest);
    return `<div class="rsv"><div class="ph"><button class="bb ib" data-a="back" aria-label="Back">${ico("back")}</button><div class="tt"><small>${P.wk.day?"Day "+P.wk.day+" · ":""}${P.wk.name}</small><b id="tm">${_pad(el/60|0)}:${_pad(el%60)}</b></div><button class="lnk" data-a="fin2">Finish</button></div>
<div class="rvk">Rest</div>
<div class="rvr" id="rvr"><svg viewBox="0 0 120 120" aria-hidden="true"><circle class="rvt" cx="60" cy="60" r="54"/><circle class="rvp" id="rvp" cx="60" cy="60" r="54" stroke-dasharray="${C}" stroke-dashoffset="${(C*(1-P.rest/P.rmax)).toFixed(1)}"/></svg><div class="rvc"><b id="rest">${Math.ceil(P.rest)}</b><span>seconds</span></div></div>
<div class="rvn">${vbox(x.k)}<div><small>${lab}</small><b>${e.n}</b><span>${what}</span></div></div>
${isNew?`<ol class="steps">${e.ins.slice(0,3).map(t=>`<li><span>${t}</span></li>`).join("")}</ol>`:""}
<div style="height:120px"></div>
<div class="dock"><div class="rvd"><button class="btn gh" data-a="more">+15s</button><button class="btn" data-a="skip">Skip</button></div></div></div>`}

  const _play=playV;
  playV=function(){
    const P=V.P;
    if(P&&!P.d0){P.d0=1;                                   /* workout just started: demo for the first exercise too */
      const n=nextSet();if(n&&S.dm!==0&&P.demo==null&&!P.log.length){P.demo=n[0];P.dt=DEMO}}
    if(P&&P.rest>0){
      const h=restV(P);
      if(h){if(!P.rs){P.rs=1;P.sy=window.scrollY||0;window.scrollTo(0,0)}return h}}
    if(P){P.rmax=0;
      if(P.rs){P.rs=0;const y=P.sy||0;if(P.demo==null)requestAnimationFrame(()=>{if(V.view==="play"&&V.P===P)window.scrollTo(0,y)})}}
    return _play.apply(this,arguments)};

  /* the demo countdown keeps running behind the rest screen; when it ends there, do not redraw (no flicker) */
  const _dms=ACT.dms;
  ACT.dms=function(){const P=V.P;if(P&&P.rest>0&&P.demo!=null){P.o=P.demo;P.demo=null;return}return _dms&&_dms.apply(this,arguments)};
  /* "+15s" must also grow the ring */
  const _more=ACT.more;
  ACT.more=function(){const r=_more.apply(this,arguments),P=V.P;if(P)P.rmax=Math.max(P.rmax||0,P.rest);return r};

  setInterval(()=>{const P=V.P;if(V.view!=="play"||!P||!(P.rest>0))return;
    const p=document.getElementById("rvp"),w=document.getElementById("rvr");if(!p)return;
    P.rmax=Math.max(P.rmax||0,P.rest);
    p.setAttribute("stroke-dashoffset",(C*(1-P.rest/P.rmax)).toFixed(1));
    if(w)w.classList.toggle("end",P.rest<=3)},100);
})();
</script>

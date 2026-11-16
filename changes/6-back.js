<script>
/* ===== Android back button: go back one screen; exit only from Home (press twice) ===== */
(function(){
  const nat=()=>{try{return !!(window.Capacitor&&window.Capacitor.isNativePlatform&&window.Capacitor.isNativePlatform())}catch(e){return false}};
  const cpl=n=>{try{const C=window.Capacitor;return (C.Plugins&&C.Plugins[n])||C.registerPlugin(n)}catch(e){return null}};
  const snap=()=>({tab:V.tab,view:V.view,xk:V.xk,ws:V.ws,xt:V.xt,bko:V.bko,cbo:V.cbo,login:V.login});
  const key=s=>[s.tab,s.view,s.xk,s.ws,s.bko,s.cbo,s.login].join("|");
  let stack=[],cur=snap(),restoring=false,lastBack=0;

  /* remember where the user came from every time the screen changes */
  const _r=window.render;
  window.render=function(){
    const out=_r.apply(this,arguments);
    if(restoring||!S.ob){cur=snap();return out}
    const n=snap(),top=stack[stack.length-1];
    if(key(n)!==key(cur)){
      if(top&&key(top)===key(n))stack.pop();                            /* returned to the previous screen */
      else if(n.view!==""||n.bko||n.cbo||n.login){stack.push(cur);if(stack.length>40)stack.shift()}   /* went deeper */
      else stack=[];                                                    /* a main tab screen: fresh start */
      cur=n}
    return out};

  function apply(p){
    restoring=true;
    V.tab=p.tab;V.view=p.view;V.xk=p.xk;V.ws=p.ws;V.xt=p.xt;V.bko=p.bko;V.cbo=p.cbo;V.login=p.login;if(p.view!=="play")V.P=null;
    window.scrollTo(0,0);try{render()}finally{restoring=false}cur=snap()}

  /* returns true when the press was used inside the app, false when the app should close */
  function handleBack(){
    if(window.__forgeOB&&window.__forgeOB.active())return window.__forgeOB.onBack();
    if(V.view==="play"&&V.P){
      if(confirm("Leave this workout? Your current session will not be saved.")){V.P=null;stack=stack.filter(s=>s.view!=="play");const p=stack.pop();apply(p||{tab:V.tab==="custom"?"custom":"home",view:"",bko:0,cbo:0,login:0})}
      return true}
    if(V.view==="fin"){stack=[];apply({tab:"home",view:"",bko:0,cbo:0,login:0});return true}
    while(stack.length&&stack[stack.length-1].view==="play")stack.pop();
    if(stack.length){apply(stack.pop());return true}
    if(V.view||V.bko||V.login||(V.tab==="custom"&&V.cbo)){apply({tab:V.tab,view:"",bko:0,cbo:0,login:0});return true}
    if(V.tab!=="home"){apply({tab:"home",view:"",bko:0,cbo:0,login:0});return true}
    /* on Home: first press warns, second press (within 2 s) exits */
    const t=Date.now();if(t-lastBack<2000)return false;
    lastBack=t;try{toast("Press back again to exit")}catch(e){}return true}
  window.__forgeBack=handleBack;window.__forgeNavStack=()=>({stack:stack.map(key),cur:key(cur)});

  if(nat()){const A=cpl("App");
    try{A&&A.addListener("backButton",()=>{let used=true;try{used=handleBack()}catch(e){used=false}if(!used){try{A.exitApp()}catch(e){}}})}catch(e){}}
})();
</script>

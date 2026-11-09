(function(){
'use strict';
if(window.__bmGame){try{window.__bmGame.destroy();}catch(e){}return;}
var URLS=['https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js','https://cdn.jsdelivr.net/npm/three@0.128.0/build/three.min.js'];
var oldT=window.THREE;

function mk(tag,css,txt,par){
  var d=document.createElement(tag);
  if(css)d.style.cssText=css;
  if(txt!=null)d.textContent=txt;
  if(par)par.appendChild(d);
  return d;
}
function toast(m,keep){
  var d=mk('div','position:fixed;left:0;right:0;top:16px;margin:0 auto;width:max-content;max-width:88vw;z-index:2147483647;padding:10px 16px;border-radius:8px;background:rgba(20,30,50,0.92);color:rgb(255,255,255);font:14px system-ui,Arial,sans-serif;box-shadow:0 4px 20px rgba(0,0,0,0.4)',m,document.body);
  if(!keep)setTimeout(function(){d.remove();},9000);
  return d;
}

var loading=toast('Loading 3D engine...',true);
function done(T){
  loading.remove();
  try{init(T);}catch(e){
    toast('Could not start the game: '+(e&&e.message?e.message:e));
    try{console.error(e);}catch(_){}
  }
}
function tryLoad(i){
  if(i>=URLS.length){
    loading.remove();
    toast('Could not load Three.js. This page may block external scripts (CSP), or you are offline. Try the bookmarklet on a different website.');
    return;
  }
  var s;
  try{s=document.createElement('script');s.src=URLS[i];}catch(e){tryLoad(i+1);return;}
  s.onload=function(){
    var T=window.THREE;
    s.remove();
    if(oldT)window.THREE=oldT;
    if(T&&T.REVISION==='128'){window.__bmT=T;done(T);}else tryLoad(i+1);
  };
  s.onerror=function(){s.remove();tryLoad(i+1);};
  (document.head||document.documentElement).appendChild(s);
}
if(window.THREE&&window.THREE.REVISION==='128')done(window.THREE);
else if(window.__bmT)done(window.__bmT);
else tryLoad(0);

function init(T){
  var V3=T.Vector3,Q4=T.Quaternion,PI=Math.PI;
  /* ---------- constants ---------- */
  var G=9.81,HF=1.5,SHOT=34,MAXB=80,BR=0.35,BM=4,BI=0.4*BM*BR*BR;
  var CM=30,CI=CM*4*HF*HF/6,DT=1/240,EYE=1.7;
  var CSTART=new V3(0,HF,-6),CYAW=0.35;

  /* ---------- renderer / DOM ---------- */
  var MINW=380,MINH=280,maxed=false,sv=null,drag=null,needFit=false;
  function vw(){return document.documentElement.clientWidth||window.innerWidth;}
  function vh(){return window.innerHeight;}
  function clamp(x,a,b){return x<a?a:(x>b?b:x);}
  var WR={w:Math.max(MINW,Math.min(980,vw()-40)),h:Math.max(MINH,Math.min(640,vh()-40)),x:0,y:0};
  WR.x=Math.max(0,Math.round((vw()-WR.w)/2));WR.y=Math.max(0,Math.round((vh()-WR.h)/2));
  var win=mk('div','position:fixed;z-index:2147483647;display:flex;flex-direction:column;background:rgb(20,28,44);box-shadow:0 14px 56px rgba(0,0,0,0.55),0 0 0 1px rgba(255,255,255,0.2);overflow:hidden;color:rgb(255,255,255);user-select:none;-webkit-user-select:none;box-sizing:border-box');
  function applyWin(){
    win.style.left=WR.x+'px';win.style.top=WR.y+'px';win.style.width=WR.w+'px';win.style.height=WR.h+'px';
    win.style.borderRadius=maxed?'0':'10px';
  }
  applyWin();
  var tbar=mk('div','height:34px;flex:none;display:flex;align-items:center;padding:0 8px 0 14px;background:rgb(30,42,66);cursor:move;border-bottom:1px solid rgba(255,255,255,0.12);box-sizing:border-box',null,win);
  mk('div','flex:1;min-width:0;font:600 13px system-ui,Segoe UI,Arial,sans-serif;overflow:hidden;white-space:nowrap;text-overflow:ellipsis','Physics Sandbox',tbar);
  var bstyle='cursor:pointer;margin-left:6px;padding:3px 10px;border:1px solid rgba(255,255,255,0.3);border-radius:6px;background:rgba(255,255,255,0.08);color:rgb(255,255,255);font:12px system-ui,Segoe UI,Arial,sans-serif';
  var bSound=mk('button',bstyle,'Sound: On',tbar);
  var bPause=mk('button',bstyle,'Pause',tbar);
  var bReset=mk('button',bstyle,'Reset',tbar);
  var bMax=mk('button',bstyle,'Maximize',tbar);
  var bExit=mk('button',bstyle+';background:rgba(200,60,60,0.55)','Close',tbar);
  var root=mk('div','position:relative;flex:1 1 auto;min-height:0;overflow:hidden;background:rgb(150,190,230);cursor:default;font:13px/1.5 ui-monospace,Menlo,Consolas,monospace',null,win);
  var R=new T.WebGLRenderer({antialias:true,powerPreference:'high-performance'});
  R.setPixelRatio(Math.min(window.devicePixelRatio||1,2));
  R.shadowMap.enabled=true;
  R.shadowMap.type=T.PCFSoftShadowMap;
  R.outputEncoding=T.sRGBEncoding;
  var cvs=R.domElement;
  cvs.style.cssText='display:block;position:absolute;left:0;top:0;outline:none';
  root.appendChild(cvs);
  /* resize handles on every edge and corner */
  var HD=[
    ['right:0;top:12px;bottom:12px;width:6px;cursor:ew-resize',0,1,0,0],
    ['left:0;top:12px;bottom:12px;width:6px;cursor:ew-resize',1,0,0,0],
    ['bottom:0;left:12px;right:12px;height:6px;cursor:ns-resize',0,0,0,1],
    ['top:0;left:12px;right:12px;height:4px;cursor:ns-resize',0,0,1,0],
    ['right:0;bottom:0;width:18px;height:18px;cursor:nwse-resize',0,1,0,1],
    ['left:0;bottom:0;width:14px;height:14px;cursor:nesw-resize',1,0,0,1],
    ['right:0;top:0;width:14px;height:10px;cursor:nesw-resize',0,1,1,0],
    ['left:0;top:0;width:14px;height:10px;cursor:nwse-resize',1,0,1,0]
  ];
  HD.forEach(function(d,i){
    var hd=mk('div','position:absolute;z-index:6;'+d[0],null,win);
    hd.addEventListener('mousedown',function(e){startRz(e,d);});
    if(i===4)mk('div','position:absolute;right:3px;bottom:3px;width:0;height:0;border-style:solid;border-width:0 0 11px 11px;border-color:transparent transparent rgba(255,255,255,0.65) transparent;pointer-events:none',null,hd);
  });
  function addDrag(){document.addEventListener('mousemove',dMove,true);document.addEventListener('mouseup',dUp,true);}
  function startDrag(e){
    if(e.button!==0||maxed||e.target.tagName==='BUTTON')return;
    e.preventDefault();
    drag={mode:0,sx:e.clientX,sy:e.clientY,x:WR.x,y:WR.y};addDrag();
  }
  function startRz(e,d){
    if(e.button!==0||maxed)return;
    e.preventDefault();e.stopPropagation();
    drag={mode:1,d:d,sx:e.clientX,sy:e.clientY,r:{x:WR.x,y:WR.y,w:WR.w,h:WR.h}};addDrag();
  }
  function dMove(e){
    if(!drag)return;
    var dx=e.clientX-drag.sx,dy=e.clientY-drag.sy;
    if(drag.mode===0){
      WR.x=clamp(drag.x+dx,90-WR.w,vw()-90);
      WR.y=clamp(drag.y+dy,0,vh()-34);
    }else{
      var d=drag.d,r=drag.r,w=r.w,h=r.h;
      if(d[2])w=r.w+dx;
      if(d[1])w=r.w-dx;
      if(d[4])h=r.h+dy;
      if(d[3])h=r.h-dy;
      w=clamp(w,MINW,Math.max(MINW,vw()));h=clamp(h,MINH,Math.max(MINH,vh()));
      WR.w=w;WR.h=h;
      if(d[1])WR.x=r.x+r.w-w;
      if(d[3])WR.y=r.y+r.h-h;
      needFit=true;
    }
    applyWin();
  }
  function dUp(){
    drag=null;
    document.removeEventListener('mousemove',dMove,true);
    document.removeEventListener('mouseup',dUp,true);
  }
  function toggleMax(){
    if(!maxed){sv={x:WR.x,y:WR.y,w:WR.w,h:WR.h};maxed=true;WR.x=0;WR.y=0;WR.w=vw();WR.h=vh();bMax.textContent='Restore';}
    else{WR.x=sv.x;WR.y=sv.y;WR.w=sv.w;WR.h=sv.h;maxed=false;bMax.textContent='Maximize';}
    applyWin();needFit=true;
  }
  function onWinResize(){
    if(maxed){WR.w=vw();WR.h=vh();}
    else{WR.w=Math.min(WR.w,Math.max(MINW,vw()));WR.h=Math.min(WR.h,Math.max(MINH,vh()));WR.x=clamp(WR.x,90-WR.w,vw()-90);WR.y=clamp(WR.y,0,Math.max(0,vh()-34));}
    applyWin();needFit=true;
  }
  tbar.addEventListener('mousedown',startDrag);
  tbar.addEventListener('dblclick',function(e){if(e.target.tagName!=='BUTTON')toggleMax();});
  bMax.onclick=toggleMax;
  bPause.onclick=function(){togglePauseMenu(!settings.paused);};
  bReset.onclick=function(){reset();sReset();};
  bExit.onclick=function(){destroy();};
  bSound.onclick=function(){audioInit();setMuted(!muted);};
  document.body.appendChild(win);

  /* ---------- scene, sky, fog ---------- */
  var scene=new T.Scene();
  var HOR=0xa9cdee;
  scene.fog=new T.Fog(HOR,70,520);
  var SUN=new V3(50,80,60);
  var sunDir=SUN.clone().normalize();
  var sky=new T.Mesh(new T.SphereGeometry(1000,32,16),new T.ShaderMaterial({
    side:T.BackSide,depthWrite:false,fog:false,
    uniforms:{top:{value:new T.Color(0x1f5fc4)},mid:{value:new T.Color(0x6aa9ea)},hor:{value:new T.Color(HOR)},sun:{value:sunDir}},
    vertexShader:'varying vec3 vP;void main(){vP=normalize(position);gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}',
    fragmentShader:'uniform vec3 top;uniform vec3 mid;uniform vec3 hor;uniform vec3 sun;varying vec3 vP;void main(){vec3 d=normalize(vP);float h=clamp(d.y,0.0,1.0);vec3 c=mix(hor,mid,smoothstep(0.0,0.22,h));c=mix(c,top,smoothstep(0.15,0.85,h));float s=max(dot(d,sun),0.0);c+=vec3(1.0,0.85,0.6)*pow(s,6.0)*0.18+vec3(1.0,0.95,0.8)*pow(s,400.0)*2.0;if(d.y<0.0)c=hor;gl_FragColor=vec4(c,1.0);}'
  }));
  sky.renderOrder=-10;sky.frustumCulled=false;
  scene.add(sky);

  /* distant hazy mountains for horizon depth */
  var mtn=new T.Group(),mi,mm;
  var mmats=[new T.MeshBasicMaterial({color:0x3566ad,fog:false}),new T.MeshBasicMaterial({color:0x2b5a9f,fog:false})];
  for(mi=0;mi<28;mi++){
    var ang=mi/28*PI*2+Math.sin(mi*12.9)*0.1,rad=880+((mi*37)&63);
    var mh=50+Math.abs(Math.sin(mi*5.3))*110,mw=110+Math.abs(Math.cos(mi*3.1))*120;
    mm=new T.Mesh(new T.ConeGeometry(mw,mh,6+(mi&3)),mmats[mi&1]);
    mm.position.set(Math.cos(ang)*rad,mh/2-2,Math.sin(ang)*rad);
    mm.rotation.y=mi;
    mtn.add(mm);
  }
  scene.add(mtn);

  /* ---------- lights ---------- */
  scene.add(new T.HemisphereLight(0xbcd8ff,0x5f6b55,0.85));
  var sun=new T.DirectionalLight(0xfff0d4,1.25);
  sun.castShadow=true;
  sun.shadow.mapSize.set(2048,2048);
  var sc=sun.shadow.camera;sc.left=-42;sc.right=42;sc.top=42;sc.bottom=-42;sc.near=1;sc.far=260;
  sun.shadow.bias=-0.0004;sun.shadow.normalBias=0.04;
  scene.add(sun);scene.add(sun.target);
  var flashL=new T.PointLight(0xffa04a,0,32,2);
  scene.add(flashL);

  /* ---------- textures ---------- */
  var aniso=R.capabilities.getMaxAnisotropy?R.capabilities.getMaxAnisotropy():1;
  function canvasTex(w,h,draw){
    var c=mk('canvas');c.width=w;c.height=h;
    draw(c.getContext('2d'),w,h);
    var t=new T.CanvasTexture(c);
    t.encoding=T.sRGBEncoding;t.anisotropy=aniso;
    return t;
  }
  var groundTex=canvasTex(1024,1024,function(g,w,h){
    g.fillStyle='rgb(84,122,104)';g.fillRect(0,0,w,h);
    var i,j,u=w/10;
    for(i=0;i<10;i++)for(j=0;j<10;j++)if((i+j)&1){g.fillStyle='rgba(0,0,0,0.07)';g.fillRect(i*u,j*u,u,u);}
    for(i=0;i<3500;i++){g.fillStyle='rgba(255,255,255,'+(Math.random()*0.05)+')';g.fillRect(Math.random()*w,Math.random()*h,2,2);}
    g.strokeStyle='rgba(255,255,255,0.38)';g.lineWidth=2;
    for(i=1;i<10;i++){g.beginPath();g.moveTo(i*u,0);g.lineTo(i*u,h);g.moveTo(0,i*u);g.lineTo(w,i*u);g.stroke();}
    g.strokeStyle='rgba(255,255,255,0.85)';g.lineWidth=8;
    g.beginPath();g.moveTo(0,0);g.lineTo(0,h);g.moveTo(w,0);g.lineTo(w,h);g.moveTo(0,0);g.lineTo(w,0);g.moveTo(0,h);g.lineTo(w,h);g.stroke();
  });
  groundTex.wrapS=groundTex.wrapT=T.RepeatWrapping;
  groundTex.repeat.set(300,300);
  var ground=new T.Mesh(new T.PlaneGeometry(3000,3000),new T.MeshStandardMaterial({map:groundTex,roughness:0.92,metalness:0}));
  ground.rotation.x=-PI/2;ground.receiveShadow=true;
  scene.add(ground);

  var crateTex=canvasTex(512,512,function(g){
    g.fillStyle='rgb(176,84,34)';g.fillRect(0,0,512,512);
    g.fillStyle='rgb(206,104,44)';g.fillRect(34,34,444,444);
    g.strokeStyle='rgb(92,38,14)';g.lineWidth=16;g.strokeRect(8,8,496,496);
    g.lineWidth=10;g.strokeRect(34,34,444,444);
    g.lineWidth=18;g.beginPath();g.moveTo(40,40);g.lineTo(472,472);g.moveTo(472,40);g.lineTo(40,472);g.stroke();
    g.strokeStyle='rgba(255,210,160,0.35)';g.lineWidth=4;g.beginPath();g.moveTo(52,52);g.lineTo(460,460);g.moveTo(460,52);g.lineTo(52,460);g.stroke();
    g.fillStyle='rgb(50,22,8)';
    [[22,22],[490,22],[22,490],[490,490]].forEach(function(p){g.beginPath();g.arc(p[0],p[1],8,0,PI*2);g.fill();});
  });
  var cubeMat=new T.MeshStandardMaterial({map:crateTex,roughness:0.55,metalness:0.04,emissive:0x000000});
  var cubeM=new T.Mesh(new T.BoxGeometry(HF*2,HF*2,HF*2),cubeMat);
  cubeM.castShadow=true;cubeM.receiveShadow=true;
  scene.add(cubeM);

  var ballTex=canvasTex(256,128,function(g,w,h){
    var i,j;
    for(i=0;i<8;i++)for(j=0;j<4;j++){g.fillStyle=((i+j)&1)?'rgb(255,255,255)':'rgb(150,150,160)';g.fillRect(i*w/8,j*h/4,w/8,h/4);}
  });
  var ballGeo=new T.SphereGeometry(BR,32,20);
  var ballMats=[],bi;
  for(bi=0;bi<8;bi++)ballMats.push(new T.MeshStandardMaterial({map:ballTex,color:new T.Color().setHSL(bi/8,0.8,0.56),roughness:0.25,metalness:0.12}));

  /* ---------- camera / player ---------- */
  var cam=new T.PerspectiveCamera(75,1,0.1,2500);
  cam.rotation.order='YXZ';
  var P={stepPh:0,p:new V3(0,0,9),v:new V3(),yaw:0,pitch:-0.06,ground:true,bob:0,fov:75,status:'Standing',spd:0};
  var settings={playerBallCollision:false,paused:false};
  var keys={},locked=false,mdown=false,fireT=0;

  /* ---------- physics state ---------- */
  var cube={p:new V3(),q:new Q4(),v:new V3(),w:new V3(),sleep:false,rest:0,slide:0};
  var CL=[],CR=[],ci;
  for(ci=0;ci<8;ci++){CL.push(new V3((ci&1?1:-1)*HF,(ci&2?1:-1)*HF,(ci&4?1:-1)*HF));CR.push(new V3());}
  var balls=[];
  var tmp=new V3(),tmp2=new V3(),qa=new Q4(),axv=new V3();
  var _lc=new V3(),_cl=new V3(),_d=new V3(),_qi=new Q4(),qN=new V3(),qC=new V3();
  var nn=new V3(),rcv=new V3(),rsv=new V3(),vrel=new V3(),rcn=new V3(),tv=new V3(),rct=new V3(),rst=new V3();
  var ACT=[0,0,0,0,0,0,0,0],AJ=[0,0,0,0,0,0,0,0],AX=[0,0,0,0,0,0,0,0],AZ=[0,0,0,0,0,0,0,0];
  var BS=[0,0,0,0,0,0,0,0],KN=[0,0,0,0,0,0,0,0],KX=[0,0,0,0,0,0,0,0],KZ=[0,0,0,0,0,0,0,0];

  /* ---------- sound (synthesised with Web Audio, no files) ---------- */
  var AC=null,master=null,nbuf=null,muted=false,voices=0,slideG=null,slideF=null,slideS=null,simT=0,cubeSndT=-1;
  function audioInit(){
    if(AC){if(AC.state==='suspended')AC.resume();return;}
    try{
      AC=new (window.AudioContext||window.webkitAudioContext)();
      master=AC.createGain();master.gain.value=muted?0:0.8;
      var cp=AC.createDynamicsCompressor();master.connect(cp);cp.connect(AC.destination);
      nbuf=AC.createBuffer(1,AC.sampleRate*2,AC.sampleRate);
      var d=nbuf.getChannelData(0),i;
      for(i=0;i<d.length;i++)d[i]=Math.random()*2-1;
      slideS=AC.createBufferSource();slideS.buffer=nbuf;slideS.loop=true;
      slideF=AC.createBiquadFilter();slideF.type='bandpass';slideF.frequency.value=400;slideF.Q.value=0.8;
      slideG=AC.createGain();slideG.gain.value=0;
      slideS.connect(slideF);slideF.connect(slideG);slideG.connect(master);slideS.start();
      if(AC.state==='suspended')AC.resume();
    }catch(e){AC=null;}
  }
  function setMuted(m){
    muted=m;
    if(master)master.gain.value=m?0:0.8;
    bSound.textContent='Sound: '+(m?'Off':'On');
  }
  /* distance + stereo position of a sound source relative to the player */
  function dest(pos){
    var g=AC.createGain(),v=1,pan=0;
    if(pos){
      var dx=pos.x-cam.position.x,dy=pos.y-cam.position.y,dz=pos.z-cam.position.z,d=Math.sqrt(dx*dx+dy*dy+dz*dz)||0.01;
      v=1/(1+d*0.05);
      pan=clamp((dx*Math.cos(P.yaw)-dz*Math.sin(P.yaw))/d,-1,1)*0.85;
    }
    g.gain.value=v;
    if(AC.createStereoPanner){var sp=AC.createStereoPanner();sp.pan.value=pan;g.connect(sp);sp.connect(master);}
    else g.connect(master);
    return g;
  }
  function envelope(t,vol,dur){
    var e=AC.createGain();
    e.gain.setValueAtTime(0.0001,t);
    e.gain.exponentialRampToValueAtTime(Math.max(0.0002,vol),t+0.004);
    e.gain.exponentialRampToValueAtTime(0.0001,t+dur);
    return e;
  }
  function tone(type,f0,f1,dur,vol,pos){
    if(!AC||muted||voices>30||vol<0.003)return;
    var t=AC.currentTime,o=AC.createOscillator(),e=envelope(t,vol,dur);
    o.type=type;o.frequency.setValueAtTime(f0,t);o.frequency.exponentialRampToValueAtTime(Math.max(1,f1),t+dur);
    o.connect(e);e.connect(dest(pos));
    voices++;o.onended=function(){voices--;};
    o.start(t);o.stop(t+dur+0.03);
  }
  function noise(ftype,f0,f1,q,dur,vol,pos){
    if(!AC||muted||voices>30||vol<0.003)return;
    var t=AC.currentTime,s=AC.createBufferSource(),f=AC.createBiquadFilter(),e=envelope(t,vol,dur);
    s.buffer=nbuf;f.type=ftype;f.Q.value=q;
    f.frequency.setValueAtTime(f0,t);f.frequency.exponentialRampToValueAtTime(Math.max(20,f1),t+dur);
    s.connect(f);f.connect(e);e.connect(dest(pos));
    voices++;s.onended=function(){voices--;};
    s.start(t,Math.random()*1.5,dur+0.03);
  }
  function sShoot(){tone('sawtooth',430,70,0.16,0.2);tone('sine',170,48,0.22,0.4);noise('highpass',2600,800,0.7,0.09,0.2);}
  function sBounce(sp,pos){var v=Math.min(1,sp/14);tone('sine',150*(0.9+Math.random()*0.25),58,0.14,0.55*v+0.04,pos);noise('lowpass',1000,200,0.7,0.07,0.3*v,pos);}
  function sBallHit(sp,pos){
    var v=Math.min(1,sp/26);
    tone('sine',115,40,0.42,0.95*v+0.05,pos);
    noise('lowpass',1600,250,0.8,0.3,0.8*v,pos);
    noise('bandpass',2400,1800,1.2,0.07,0.55*v,pos);
    tone('triangle',520+Math.random()*90,300,0.18,0.13*v,pos);
  }
  function sThud(sp,pos){var v=Math.min(1,sp/8);tone('sine',92,34,0.36,0.9*v,pos);noise('lowpass',750,150,0.7,0.22,0.65*v,pos);}
  function sClick(sp,pos){tone('triangle',900+Math.random()*250,520,0.05,0.14*Math.min(1,sp/8),pos);}
  function sJump(){tone('sine',230,430,0.12,0.12);}
  function sLand(v){noise('lowpass',520,120,0.7,0.15,0.4*v);tone('sine',95,45,0.16,0.28*v);}
  function sStep(){noise('lowpass',430,160,0.8,0.06,0.1);}
  function sReset(){tone('sine',260,760,0.2,0.16);}
  function updateSlide(){
    if(!AC)return;
    var hs=0,tg=0,dx,dz;
    if(!muted&&cube.slide){
      hs=Math.sqrt(cube.v.x*cube.v.x+cube.v.z*cube.v.z);
      dx=cube.p.x-cam.position.x;dz=cube.p.z-cam.position.z;
      if(hs>0.2)tg=Math.min(0.55,hs*0.08)/(1+Math.sqrt(dx*dx+dz*dz)*0.05);
    }
    slideG.gain.setTargetAtTime(tg,AC.currentTime,0.05);
    slideF.frequency.setTargetAtTime(240+hs*95,AC.currentTime,0.05);
  }

  function clampH(x){return x<-HF?-HF:(x>HF?HF:x);}

  /* sphere (centre c, radius r) vs the cube. returns penetration depth (0 = none).
     sets qN (outward normal, cube -> sphere) and qC (contact point on cube surface) */
  function boxQ(c,r){
    _qi.copy(cube.q).conjugate();
    _lc.copy(c).sub(cube.p).applyQuaternion(_qi);
    _cl.set(clampH(_lc.x),clampH(_lc.y),clampH(_lc.z));
    _d.copy(_lc).sub(_cl);
    var dd=_d.lengthSq(),pen,s;
    if(dd>r*r)return 0;
    if(dd>1e-10){var dist=Math.sqrt(dd);_d.multiplyScalar(1/dist);pen=r-dist;}
    else{
      var ax=Math.abs(_lc.x),ay=Math.abs(_lc.y),az=Math.abs(_lc.z);
      _d.set(0,0,0);
      if(ax>=ay&&ax>=az){s=_lc.x<0?-1:1;_d.x=s;_cl.x=s*HF;pen=r+HF-ax;}
      else if(ay>=az){s=_lc.y<0?-1:1;_d.y=s;_cl.y=s*HF;pen=r+HF-ay;}
      else{s=_lc.z<0?-1:1;_d.z=s;_cl.z=s*HF;pen=r+HF-az;}
    }
    qN.copy(_d).applyQuaternion(cube.q);
    qC.copy(_cl).applyQuaternion(cube.q).add(cube.p);
    return pen;
  }

  /* ---------- effects ---------- */
  var NP=700,pPos=new Float32Array(NP*3),pCol=new Float32Array(NP*3),pBase=new Float32Array(NP*3),pVel=new Float32Array(NP*3),pLife=new Float32Array(NP),pMax=new Float32Array(NP),pIdx=0,k;
  for(k=0;k<NP;k++){pPos[k*3+1]=-500;pMax[k]=1;}
  var pGeo=new T.BufferGeometry();
  pGeo.setAttribute('position',new T.BufferAttribute(pPos,3));
  pGeo.setAttribute('color',new T.BufferAttribute(pCol,3));
  var spr=canvasTex(64,64,function(g){
    var gr=g.createRadialGradient(32,32,0,32,32,32);
    gr.addColorStop(0,'rgba(255,255,255,1)');gr.addColorStop(0.4,'rgba(255,255,255,0.55)');gr.addColorStop(1,'rgba(255,255,255,0)');
    g.fillStyle=gr;g.fillRect(0,0,64,64);
  });
  var pts=new T.Points(pGeo,new T.PointsMaterial({size:0.3,map:spr,vertexColors:true,transparent:true,depthWrite:false,blending:T.AdditiveBlending}));
  pts.frustumCulled=false;scene.add(pts);
  var rings=[],rg=new T.RingGeometry(0.93,1,48),rn;
  for(rn=0;rn<6;rn++){
    var rmesh=new T.Mesh(rg,new T.MeshBasicMaterial({color:0xffc277,transparent:true,opacity:0,blending:T.AdditiveBlending,depthWrite:false,side:T.DoubleSide,fog:false}));
    rmesh.visible=false;scene.add(rmesh);rings.push({m:rmesh,t:9,pw:0});
  }
  var ringIdx=0,fl={l:0,c:0};

  function impact(p,n,pw){
    var cnt=12+Math.floor(pw*52),i,idx,sp,dx,dy,dz,dl;
    for(i=0;i<cnt;i++){
      idx=pIdx;pIdx=(pIdx+1)>=NP?0:pIdx+1;
      dx=n.x*(0.7+Math.random())+(Math.random()*2-1)*0.9;
      dy=n.y*(0.7+Math.random())+(Math.random()*2-1)*0.9;
      dz=n.z*(0.7+Math.random())+(Math.random()*2-1)*0.9;
      dl=Math.sqrt(dx*dx+dy*dy+dz*dz)||1;
      sp=2+Math.random()*(4+pw*13);
      pVel[idx*3]=dx/dl*sp;pVel[idx*3+1]=dy/dl*sp;pVel[idx*3+2]=dz/dl*sp;
      pPos[idx*3]=p.x;pPos[idx*3+1]=p.y;pPos[idx*3+2]=p.z;
      pBase[idx*3]=1;pBase[idx*3+1]=0.55+Math.random()*0.4;pBase[idx*3+2]=0.12+Math.random()*0.25;
      pLife[idx]=pMax[idx]=0.35+Math.random()*0.55;
    }
    fl.l=Math.max(fl.l,0.45+pw);
    fl.c=Math.max(fl.c,0.18+pw*0.45);
    flashL.position.copy(p).addScaledVector(n,1.2);
    var r=rings[ringIdx];ringIdx=ringIdx+1>=rings.length?0:ringIdx+1;
    r.t=0;r.pw=pw;r.m.visible=true;
    r.m.position.copy(p).addScaledVector(n,0.03);
    r.m.lookAt(tmp.copy(p).add(n));
  }

  function updateFx(dt){
    var i,j,a;
    for(i=0;i<NP;i++){
      j=i*3;
      if(pLife[i]>0){
        pLife[i]-=dt;
        pVel[j+1]-=12*dt;
        a=Math.max(0,1-1.6*dt);pVel[j]*=a;pVel[j+1]*=a;pVel[j+2]*=a;
        pPos[j]+=pVel[j]*dt;pPos[j+1]+=pVel[j+1]*dt;pPos[j+2]+=pVel[j+2]*dt;
        if(pPos[j+1]<0.02){pPos[j+1]=0.02;pVel[j+1]*=-0.4;pVel[j]*=0.6;pVel[j+2]*=0.6;}
        a=Math.max(0,pLife[i]/pMax[i]);
        pCol[j]=pBase[j]*a;pCol[j+1]=pBase[j+1]*a;pCol[j+2]=pBase[j+2]*a;
      }else if(pCol[j]>0||pCol[j+1]>0||pCol[j+2]>0){pCol[j]=pCol[j+1]=pCol[j+2]=0;}
    }
    pGeo.attributes.position.needsUpdate=true;
    pGeo.attributes.color.needsUpdate=true;
    for(i=0;i<rings.length;i++){
      var r=rings[i];
      if(!r.m.visible)continue;
      r.t+=dt;
      if(r.t>0.5){r.m.visible=false;continue;}
      a=0.3+r.t*(3.2+r.pw*4.5);
      r.m.scale.set(a,a,a);
      r.m.material.opacity=(1-r.t/0.5)*(0.25+r.pw*0.3);
    }
    var ek=Math.exp(-9*dt);fl.l*=ek;flashL.intensity=fl.l*3.2;
    fl.c*=Math.exp(-4.5*dt);
    cubeMat.emissive.setRGB(fl.c,fl.c*0.42,fl.c*0.08);
  }

  /* ---------- cube physics ---------- */
  function stepCube(h){
    var c=cube;
    if(c.sleep){c.slide=0;return;}
    c.v.y-=G*h;
    var sp=c.v.length(),df=Math.max(0,1-(0.08+0.012*sp)*h);
    c.v.multiplyScalar(df);
    c.w.multiplyScalar(Math.max(0,1-0.2*h));
    var n=0,i,r,kk,it,vn,j,nj,lim,imp=0;
    for(i=0;i<8;i++){
      r=CR[i].copy(CL[i]).applyQuaternion(c.q);
      if(c.p.y+r.y<0.004){ACT[n]=i;n++;}
    }
    if(n>0){
      for(kk=0;kk<n;kk++){
        r=CR[ACT[kk]];
        vn=c.v.y+c.w.z*r.x-c.w.x*r.z;
        BS[kk]=vn<-1.2?-0.22*vn:0;
        if(-vn>imp)imp=-vn;
        KN[kk]=1/CM+(r.x*r.x+r.z*r.z)/CI;
        KX[kk]=1/CM+(r.y*r.y+r.z*r.z)/CI;
        KZ[kk]=1/CM+(r.x*r.x+r.y*r.y)/CI;
        AJ[kk]=0;AX[kk]=0;AZ[kk]=0;
      }
      for(it=0;it<12;it++){
        for(kk=0;kk<n;kk++){
          r=CR[ACT[kk]];
          vn=c.v.y+c.w.z*r.x-c.w.x*r.z;
          j=(BS[kk]-vn)/KN[kk];
          nj=Math.max(0,AJ[kk]+j);j=nj-AJ[kk];AJ[kk]=nj;
          c.v.y+=j/CM;c.w.x+=-r.z*j/CI;c.w.z+=r.x*j/CI;
          lim=0.55*AJ[kk];
          vn=c.v.x+c.w.y*r.z-c.w.z*r.y;
          j=-vn/KX[kk];
          nj=Math.max(-lim,Math.min(lim,AX[kk]+j));j=nj-AX[kk];AX[kk]=nj;
          c.v.x+=j/CM;c.w.y+=r.z*j/CI;c.w.z+=-r.y*j/CI;
          vn=c.v.z+c.w.x*r.y-c.w.y*r.x;
          j=-vn/KZ[kk];
          nj=Math.max(-lim,Math.min(lim,AZ[kk]+j));j=nj-AZ[kk];AZ[kk]=nj;
          c.v.z+=j/CM;c.w.x+=r.y*j/CI;c.w.y+=-r.x*j/CI;
        }
      }
      c.w.multiplyScalar(Math.max(0,1-0.6*h));
      if(imp>1.5&&simT-cubeSndT>0.12){cubeSndT=simT;sThud(imp,c.p);}
    }
    c.slide=n>0?1:0;
    /* integrate */
    c.p.addScaledVector(c.v,h);
    var q=c.q,w=c.w,hh=0.5*h;
    var qx=q.x,qy=q.y,qz=q.z,qw=q.w;
    q.x+=hh*(w.x*qw+w.y*qz-w.z*qy);
    q.y+=hh*(w.y*qw+w.z*qx-w.x*qz);
    q.z+=hh*(w.z*qw+w.x*qy-w.y*qx);
    q.w+=hh*(-(w.x*qx+w.y*qy+w.z*qz));
    q.normalize();
    var minY=1e9;
    for(i=0;i<8;i++){r=CR[i].copy(CL[i]).applyQuaternion(q);if(c.p.y+r.y<minY)minY=c.p.y+r.y;}
    if(minY<0)c.p.y-=minY;
    /* sleeping */
    if(n>=3&&c.v.lengthSq()<0.0036&&c.w.lengthSq()<0.0064){
      c.rest+=h;
      if(c.rest>0.45){c.sleep=true;c.v.set(0,0,0);c.w.set(0,0,0);}
    }else c.rest=0;
  }

  /* ---------- ball physics ---------- */
  function ballCube(b){
    if(b.p.distanceToSquared(cube.p)>9.5)return;
    var pen=boxQ(b.p,BR);
    if(pen<=0)return;
    b.p.addScaledVector(qN,pen);
    var n=nn.copy(qN),rc=rcv.copy(qC).sub(cube.p),rs=rsv.copy(n).multiplyScalar(-BR);
    vrel.copy(b.v).add(tv.crossVectors(b.w,rs)).sub(cube.v).sub(tv.crossVectors(cube.w,rc));
    var vn=vrel.dot(n);
    if(vn>=0)return;
    if(vn<-0.5)cube.sleep=false;
    var icm=cube.sleep?0:1/CM,ici=cube.sleep?0:1/CI;
    rcn.crossVectors(rc,n);
    var K=1/BM+icm+rcn.lengthSq()*ici;
    var e=vn<-2?0.35:0,j=-(1+e)*vn/K;
    b.v.addScaledVector(n,j/BM);
    cube.v.addScaledVector(n,-j*icm);
    cube.w.addScaledVector(rcn,-j*ici);
    vrel.copy(b.v).add(tv.crossVectors(b.w,rs)).sub(cube.v).sub(tv.crossVectors(cube.w,rc));
    var vn2=vrel.dot(n);
    tv.copy(vrel).addScaledVector(n,-vn2);
    var tl=tv.length();
    if(tl>1e-4){
      tv.multiplyScalar(1/tl);
      rct.crossVectors(rc,tv);rst.crossVectors(rs,tv);
      var Kt=1/BM+icm+rct.lengthSq()*ici+rst.lengthSq()/BI;
      var jt=Math.min(tl/Kt,0.45*j);
      b.v.addScaledVector(tv,-jt/BM);
      b.w.addScaledVector(rst,-jt/BI);
      cube.v.addScaledVector(tv,jt*icm);
      cube.w.addScaledVector(rct,jt*ici);
    }
    if(-vn>1)sBallHit(-vn,qC);
    if(-vn>3)impact(qC,n,Math.min(1,-vn/28));
    cube.rest=0;
  }

  function stepBall(b,h){
    var v=b.v,w=b.w,p=b.p;
    v.y-=G*h;
    var sp=v.length();
    v.multiplyScalar(Math.max(0,1-(0.03+0.004*sp)*h));
    p.addScaledVector(v,h);
    var wl=w.length();
    if(wl>1e-5){qa.setFromAxisAngle(axv.copy(w).multiplyScalar(1/wl),wl*h);b.q.premultiply(qa);b.q.normalize();}
    if(p.y<BR){
      p.y=BR;
      var vn=v.y,jn=0;
      if(vn<0){var e=vn<-1.5?0.62:0;if(vn<-1.5)sBounce(-vn,p);v.y=-vn*e;jn=-(1+e)*vn*BM;}
      if(jn>0){
        var vx=v.x+w.z*BR,vz=v.z-w.x*BR,Kt=3.5/BM;
        var jx=-vx/Kt,jz=-vz/Kt,jl=Math.sqrt(jx*jx+jz*jz),lim=0.6*jn;
        if(jl>lim){jx*=lim/jl;jz*=lim/jl;}
        v.x+=jx/BM;v.z+=jz/BM;w.z+=BR*jx/BI;w.x+=-BR*jz/BI;
      }
      var hs=Math.sqrt(v.x*v.x+v.z*v.z);
      if(hs>0){var ns=Math.max(0,hs-0.9*h),kf=ns/hs;v.x*=kf;v.z*=kf;w.x*=Math.max(0,1-0.35*h);w.z*=Math.max(0,1-0.35*h);}
      w.y*=Math.max(0,1-1.2*h);
      if(hs<0.05&&w.lengthSq()<0.04){v.x=0;v.z=0;w.set(0,0,0);}
    }
    if(settings.playerBallCollision)playerBallCollision(b);
    ballCube(b);
  }

  function ballPairs(){
    var i,j,a,b,dd,dist,pen,rv,jj,min2=4*BR*BR;
    for(i=0;i<balls.length;i++){
      a=balls[i];
      for(j=i+1;j<balls.length;j++){
        b=balls[j];
        dd=(b.p.x-a.p.x)*(b.p.x-a.p.x);
        if(dd>=min2)continue;
        tmp.copy(b.p).sub(a.p);
        dd=tmp.lengthSq();
        if(dd>=min2||dd<1e-9)continue;
        dist=Math.sqrt(dd);tmp.multiplyScalar(1/dist);
        pen=2*BR-dist;
        a.p.addScaledVector(tmp,-pen/2);b.p.addScaledVector(tmp,pen/2);
        rv=tmp2.copy(b.v).sub(a.v).dot(tmp);
        if(rv<-1.5)sClick(-rv,a.p);
        if(rv<0){jj=-(1+0.55)*rv*BM/2;a.v.addScaledVector(tmp,-jj/BM);b.v.addScaledVector(tmp,jj/BM);}
      }
    }
  }

  function physics(h){
    simT+=h;
    stepCube(h);
    var i;
    for(i=0;i<balls.length;i++)stepBall(balls[i],h);
    ballPairs();
  }

  function removeBall(i){
    var b=balls[i];
    scene.remove(b.m);
    balls.splice(i,1);
  }
  function shoot(){
    if(balls.length>=MAXB)removeBall(0);
    var dir=new V3(0,0,-1).applyQuaternion(cam.quaternion);
    var pos=cam.position.clone().addScaledVector(dir,0.9);
    pos.y-=0.12;
    var m=new T.Mesh(ballGeo,ballMats[(Math.random()*ballMats.length)|0]);
    m.castShadow=true;
    var b={m:m,p:pos,v:dir.clone().multiplyScalar(SHOT),w:new V3(),q:new Q4()};
    b.v.x+=P.v.x;b.v.z+=P.v.z;
    m.position.copy(pos);
    scene.add(m);balls.push(b);
    sShoot();
  }
  function reset(){
    while(balls.length)removeBall(0);
    cube.p.copy(CSTART);
    cube.q.setFromAxisAngle(new V3(0,1,0),CYAW);
    cube.v.set(0,0,0);cube.w.set(0,0,0);cube.sleep=false;cube.rest=0;
    var i;
    for(i=0;i<NP;i++){pLife[i]=0;pCol[i*3]=pCol[i*3+1]=pCol[i*3+2]=0;}
    for(i=0;i<rings.length;i++)rings[i].m.visible=false;
    fl.l=0;fl.c=0;
  }
  reset();

  /* ---------- player ---------- */
  function playerBox(){
    var i,pen,vn;
    for(i=0;i<3;i++){
      tmp.set(P.p.x,P.p.y+0.42+i*0.5,P.p.z);
      pen=boxQ(tmp,0.42);
      if(pen>0){
        P.p.addScaledVector(qN,pen);
        vn=P.v.dot(qN);
        if(vn<0)P.v.addScaledVector(qN,-vn);
        if(qN.y>0.6)P.ground=true;
      }
    }
  }
  function playerBallCollision(b){
    if(!b||!b.p)return;
    var playerCenter=new V3(P.p.x,P.p.y+0.78,P.p.z);
    var n=tmp.copy(b.p).sub(playerCenter);
    var d2=n.lengthSq();
    var hitR=BR+0.72;
    if(d2>hitR*hitR)return;
    var d=Math.sqrt(d2)||0.0001;
    n.multiplyScalar(1/d);
    var overlap=hitR-d;
    if(overlap<=0)return;
    b.p.addScaledVector(n,overlap);
    var vn=b.v.dot(n);
    if(vn<0){
      var bounce=-(1+0.45)*vn;
      b.v.addScaledVector(n,bounce);
      if(-vn>1.1)sBallHit(-vn,b.p);
    }
    P.v.addScaledVector(n,0.18);
  }
  function updatePlayer(dt){
    if(!(dt>0))return;
    var f=((keys.KeyW||keys.ArrowUp)?1:0)-((keys.KeyS||keys.ArrowDown)?1:0);
    var s=((keys.KeyD||keys.ArrowRight)?1:0)-((keys.KeyA||keys.ArrowLeft)?1:0);
    var sy=Math.sin(P.yaw),cy=Math.cos(P.yaw);
    var wx=-sy*f+cy*s,wz=-cy*f-sy*s,wl=Math.sqrt(wx*wx+wz*wz);
    var sprint=(keys.ShiftLeft||keys.ShiftRight)&&f>0;
    var sp=sprint?11:7,tx=0,tz=0;
    if(wl>0){tx=wx/wl*sp;tz=wz/wl*sp;}
    var acc=P.ground?(wl>0?50:38):(wl>0?14:0);
    if(acc>0){
      var dx=tx-P.v.x,dz=tz-P.v.z,dl=Math.sqrt(dx*dx+dz*dz),st=acc*dt;
      if(dl<=st){P.v.x=tx;P.v.z=tz;}else{P.v.x+=dx/dl*st;P.v.z+=dz/dl*st;}
    }
    var wasG=P.ground;
    if(P.ground&&keys.Space){P.v.y=7.4;P.ground=false;sJump();}
    P.v.y-=20*dt;
    P.p.addScaledVector(P.v,dt);
    var fall=P.v.y;
    P.ground=false;
    if(P.p.y<=0){P.p.y=0;if(P.v.y<0)P.v.y=0;P.ground=true;}
    playerBox();
    if(P.ground&&!wasG&&fall<-4)sLand(Math.min(1,-fall/12));
    var hs=Math.sqrt(P.v.x*P.v.x+P.v.z*P.v.z);
    P.spd=hs;
    if(P.ground&&hs>0.5)P.bob+=hs*dt*1.5;
    var sph=Math.floor(P.bob/PI);
    if(P.ground&&hs>0.5&&sph!==P.stepPh)sStep();
    P.stepPh=sph;
    var bob=P.ground?Math.sin(P.bob)*0.035*Math.min(1,hs/7):0;
    cam.position.set(P.p.x,P.p.y+EYE+bob,P.p.z);
    cam.rotation.set(P.pitch,P.yaw,0);
    var tf=(sprint&&hs>8)?81:75;
    P.fov+=(tf-P.fov)*Math.min(1,dt*8);
    if(Math.abs(cam.fov-P.fov)>0.01){cam.fov=P.fov;cam.updateProjectionMatrix();}
    if(!P.ground)P.status=P.v.y>0.5?'Jumping':'Falling';
    else if(hs>0.5)P.status=(sprint&&hs>8)?'Sprinting':'Walking';
    else P.status='Standing';
  }

  /* ---------- HUD ---------- */
  var hud=mk('div','position:absolute;left:12px;top:12px;padding:9px 14px;background:rgba(10,20,35,0.58);border:1px solid rgba(255,255,255,0.16);border-radius:10px;white-space:pre;pointer-events:none;min-width:210px',null,root);
  var ch=mk('div','position:absolute;width:0;height:0;pointer-events:none',null,root);
  [[-12,-1,8,2],[4,-1,8,2],[-1,-12,2,8],[-1,4,2,8],[-1,-1,2,2]].forEach(function(s){
    mk('div','position:absolute;background:rgba(255,255,255,0.92);box-shadow:0 0 2px rgba(0,0,0,0.9);left:'+s[0]+'px;top:'+s[1]+'px;width:'+s[2]+'px;height:'+s[3]+'px',null,ch);
  });
  mk('div','position:absolute;left:0;right:0;bottom:10px;text-align:center;pointer-events:none;font-size:12px;padding:0 8px;color:rgba(255,255,255,0.85);text-shadow:0 1px 3px rgba(0,0,0,0.8)','WASD move   Space jump   Shift sprint   Click shoot   R reset   M mute   Esc pause',root);
  var wrap=mk('div','position:absolute;left:0;top:0;right:0;bottom:0;display:none;pointer-events:none',null,root);

  var pauseMenu=mk('div','position:absolute;left:0;top:0;right:0;bottom:0;display:none;align-items:center;justify-content:center;background:rgba(5,10,18,0.62);backdrop-filter:blur(2px);z-index:20',null,root);
  var pausePanel=mk('div','width:min(440px,88%);background:rgba(10,20,35,0.94);border:1px solid rgba(255,255,255,0.14);border-radius:16px;padding:18px 20px;box-shadow:0 25px 60px rgba(0,0,0,0.45)',null,pauseMenu);
  mk('div','font:700 30px system-ui,Segoe UI,Arial,sans-serif;margin:0 0 14px;color:#eef6ff','Pause Menu',pausePanel);
  var pauseContent=mk('div','display:grid;gap:12px',null,pausePanel);
  var pauseButtons=mk('div','display:flex;gap:10px;justify-content:flex-end;margin-top:4px',null,pauseContent);
  var resumeBtn=mk('button','border:1px solid rgba(255,255,255,0.18);border-radius:10px;background:rgba(90,199,255,0.16);color:#eef8ff;padding:9px 14px;cursor:pointer;font:600 13px system-ui,Segoe UI,Arial,sans-serif','Resume',pauseButtons);
  var settingsBtn=mk('button','border:1px solid rgba(255,255,255,0.18);border-radius:10px;background:rgba(255,255,255,0.04);color:#eef8ff;padding:9px 14px;cursor:pointer;font:600 13px system-ui,Segoe UI,Arial,sans-serif','Settings',pauseButtons);
  var resetBtn=mk('button','border:1px solid rgba(255,255,255,0.18);border-radius:10px;background:rgba(255,255,255,0.04);color:#eef8ff;padding:9px 14px;cursor:pointer;font:600 13px system-ui,Segoe UI,Arial,sans-serif','Reset',pauseButtons);
  var settingsPanel=mk('div','display:none;gap:12px;padding:10px;border-radius:10px;background:rgba(255,255,255,0.03);border:1px solid rgba(255,255,255,0.08)',null,pauseContent);
  var collisionLabel=mk('label','display:flex;align-items:center;justify-content:space-between;gap:12px;padding:10px 12px;border-radius:10px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);cursor:pointer;color:#edf6ff;font:600 14px system-ui,Segoe UI,Arial,sans-serif',null,settingsPanel);
  mk('span','flex:1','Balls collide with player',collisionLabel);
  var collisionToggle=mk('input','accent-color:#7ad3ff;transform:scale(1.2);cursor:pointer',null,collisionLabel);
  collisionToggle.type='checkbox';
  collisionToggle.checked=settings.playerBallCollision;
  collisionToggle.addEventListener('change',function(){settings.playerBallCollision=collisionToggle.checked;});
  function syncPauseMenu(){
    pauseMenu.style.display=settings.paused?'flex':'none';
    bPause.textContent=settings.paused?'Resume':'Pause';
    collisionToggle.checked=settings.playerBallCollision;
    settingsPanel.style.display=settings.paused&&settingsPanel.dataset.open==='1'?'grid':'none';
  }
  function togglePauseMenu(force){
    var next=typeof force==='boolean'?force:!settings.paused;
    settings.paused=next;
    syncPauseMenu();
    if(settings.paused){
      keys={};mdown=false;
      if(document.pointerLockElement)document.exitPointerLock();
    }
    wrap.style.display='none';
  }
  settingsBtn.onclick=function(){
    settingsPanel.dataset.open=settingsPanel.dataset.open==='1'?'0':'1';
    settingsPanel.style.display=settingsPanel.dataset.open==='1'?'grid':'none';
  };

  /* ---------- input ---------- */
  var GK={KeyW:1,KeyA:1,KeyS:1,KeyD:1,Space:1,ShiftLeft:1,ShiftRight:1,KeyR:1,KeyM:1,Escape:1,ArrowUp:1,ArrowDown:1,ArrowLeft:1,ArrowRight:1};
  function lock(){
    audioInit();
    try{var pr=cvs.requestPointerLock();if(pr&&pr.catch)pr.catch(function(){});}catch(e){}
  }
  function onKD(e){
    if(e.code==='Escape'&&!e.repeat){
      e.preventDefault();e.stopImmediatePropagation();
      togglePauseMenu(!settings.paused);
      return;
    }
    if(!locked||!GK[e.code]||e.ctrlKey||e.metaKey||e.altKey)return;
    e.preventDefault();e.stopImmediatePropagation();
    keys[e.code]=true;
    if(e.code==='KeyR'&&!e.repeat){reset();sReset();}
    if(e.code==='KeyM'&&!e.repeat)setMuted(!muted);
  }
  function onKU(e){
    if(!GK[e.code]||!locked)return;
    e.preventDefault();e.stopImmediatePropagation();
    keys[e.code]=false;
  }
  function onBlur(){keys={};mdown=false;}
  function onMM(e){
    if(!locked||settings.paused)return;
    var mx=e.movementX||0,my=e.movementY||0;
    if(Math.abs(mx)>400||Math.abs(my)>400)return;
    P.yaw-=mx*0.0022;
    P.pitch-=my*0.0022;
    if(P.pitch>1.55)P.pitch=1.55;
    if(P.pitch<-1.55)P.pitch=-1.55;
  }
  function onMD(e){
    if(!locked||settings.paused)return;
    if(e.button===0){mdown=true;fireT=0.16;shoot();}
  }
  function onMU(e){if(e.button===0)mdown=false;}
  function onPL(){
    locked=(document.pointerLockElement===cvs);
    wrap.style.display='none';
    cvs.style.cursor=locked?'none':'default';
    if(!locked&&!settings.paused){keys={};mdown=false;}
  }
  resumeBtn.onclick=function(){togglePauseMenu(false);lock();};
  resetBtn.onclick=function(){reset();sReset();togglePauseMenu(false);};
  function fit(){
    var w=root.clientWidth||WR.w,h=root.clientHeight||Math.max(50,WR.h-34);
    R.setSize(w,h);
    cam.aspect=w/h;cam.updateProjectionMatrix();
    ch.style.left=(w/2)+'px';ch.style.top=(h/2)+'px';
  }
  function noCtx(e){e.preventDefault();}
  function noWheel(e){e.preventDefault();}
  cvs.addEventListener('click',function(){if(!locked)lock();});
  win.addEventListener('contextmenu',noCtx);
  root.addEventListener('wheel',noWheel,{passive:false});
  window.addEventListener('keydown',onKD,true);
  window.addEventListener('keyup',onKU,true);
  window.addEventListener('blur',onBlur);
  window.addEventListener('resize',onWinResize);
  document.addEventListener('mousemove',onMM,true);
  document.addEventListener('mousedown',onMD,true);
  document.addEventListener('mouseup',onMU,true);
  document.addEventListener('pointerlockchange',onPL);
  fit();

  /* ---------- main loop ---------- */
  var raf=0,last=performance.now(),fCount=0,fTime=0,fps=60,hudT=0,alive=true;
  function frame(now){
    if(!alive)return;
    raf=requestAnimationFrame(frame);
    if(needFit){needFit=false;fit();}
    var dt=(now-last)/1000;last=now;
    if(!(dt>0))dt=0;
    if(dt>0.05)dt=0.05;
    fCount++;fTime+=dt;
    if(fTime>=0.5){fps=Math.round(fCount/fTime);fCount=0;fTime=0;}
    if(!settings.paused){
      updatePlayer(dt);
      if(mdown&&locked){fireT-=dt;if(fireT<=0){shoot();fireT=0.16;}}
      if(dt>0){
        var n=Math.min(12,Math.ceil(dt/DT)),h=dt/n,i;
        for(i=0;i<n;i++)physics(h);
      }
      updateFx(dt);
      updateSlide();
    }
    var i2,b;
    for(i2=balls.length-1;i2>=0;i2--){
      b=balls[i2];
      if(b.p.y<-50||Math.abs(b.p.x)>600||Math.abs(b.p.z)>600){removeBall(i2);continue;}
      b.m.position.copy(b.p);b.m.quaternion.copy(b.q);
    }
    cubeM.position.copy(cube.p);cubeM.quaternion.copy(cube.q);
    /* world follows the player so the ground and sky are endless */
    ground.position.set(Math.round(P.p.x/10)*10,0,Math.round(P.p.z/10)*10);
    sky.position.copy(cam.position);
    mtn.position.set(cam.position.x,0,cam.position.z);
    sun.target.position.set(Math.round(P.p.x),0,Math.round(P.p.z));
    sun.position.copy(sun.target.position).add(SUN);
    R.render(scene,cam);
    hudT+=dt;
    if(hudT>0.2){
      hudT=0;
      hud.textContent='FPS    '+fps+'\nBalls  '+balls.length+' / '+MAXB+'\nMove   '+P.status+' ('+P.spd.toFixed(1)+' m/s)\nCube   '+(cube.sleep?'at rest':cube.v.length().toFixed(1)+' m/s')+'\nPos    '+P.p.x.toFixed(1)+', '+P.p.y.toFixed(1)+', '+P.p.z.toFixed(1);
    }
  }

  function destroy(){
    alive=false;
    cancelAnimationFrame(raf);
    window.removeEventListener('keydown',onKD,true);
    window.removeEventListener('keyup',onKU,true);
    window.removeEventListener('blur',onBlur);
    window.removeEventListener('resize',onWinResize);
    dUp();
    document.removeEventListener('mousemove',onMM,true);
    document.removeEventListener('mousedown',onMD,true);
    document.removeEventListener('mouseup',onMU,true);
    document.removeEventListener('pointerlockchange',onPL);
    try{if(document.pointerLockElement)document.exitPointerLock();}catch(e){}
    try{R.dispose();if(R.forceContextLoss)R.forceContextLoss();}catch(e){}
    try{if(slideS)slideS.stop();if(AC)AC.close();}catch(e){}
    win.remove();
    delete window.__bmGame;
  }
  window.__bmGame={destroy:destroy,S:{P:P,cube:cube,balls:balls,keys:keys,shoot:shoot,reset:reset,cam:cam,fx:fl,setKeys:function(o){keys=o;},setLocked:function(v){locked=v;},audio:function(){return AC;},step:function(dt){updatePlayer(dt);var n=Math.ceil(dt/DT),i;for(i=0;i<n;i++)physics(dt/n);}}};
  requestAnimationFrame(function(t){last=t;frame(t);});
}
})();

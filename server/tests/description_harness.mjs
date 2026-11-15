import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import * as policy from '../web/policy.mjs';
import {ConversationStore,pendingRequest} from '../web/conversations.mjs';
const tick=()=>new Promise(resolve=>setImmediate(resolve));
const task={id:'private',title:'Private title',description:'Private description',notes:'Private notes',project:'Secret',status:'todo',priority:'medium',revision:1,deleted:false,notification:{unread:false,token:'own'}};
const response=(data,status=200)=>({ok:status===200,status,json:async()=>data});
export async function harness(){
 const elements=new Map(),calls=[];
 const element=(tagName)=>({tagName,options:[],style:{setProperty(){}},value:'',textContent:'',hidden:true,disabled:false,open:false,dataset:{},children:[],listeners:{},addEventListener(k,fn){this.listeners[k]=fn;},setAttribute(k,v){(this.attributes??={})[k]=String(v);},replaceChildren(...v){this.textContent='';this.children=v;},append(...v){this.children.push(...v);},focus(){},reset(){},close(){this.open=false;},showModal(){this.open=true;}});
 const get=id=>{if(!elements.has(id)){const e=element(),html=readFileSync(new URL('../web/index.html',import.meta.url),'utf8'),tag=html.match(new RegExp('<[^>]+id="'+id+'"[^>]*>'))?.[0]||'';for(const m of tag.matchAll(/([\w-]+)="([^"]*)"/g))e.setAttribute(m[1],m[2]);elements.set(id,e);}return elements.get(id);};
 const keys=['title','description','notes','project','due_date','status','priority'];
 get('task-form').elements={namedItem:get};get('task-form').reset=()=>keys.forEach(k=>{get(k).value='';});
 const context=vm.createContext({...policy,ConversationStore,pendingRequest,AbortController,TextEncoder,URLSearchParams,setTimeout:()=>0,clearTimeout,clearInterval,
 FormData:class{entries(){return keys.filter(k=>!get(k).disabled).map(k=>[k,get(k).value]);}},
 localStorage:{getItem(){return null;}},navigator:{},window:{addEventListener(){},confirm:()=>true,SpeechRecognition:function(){}},
 document:{querySelector(selector){return selector==='.filters'?get('filters'):null;},getElementById:get,documentElement:{dataset:{}},querySelectorAll(selector){return selector==='dialog[open]'?[...elements].filter(([id,e])=>id.endsWith('dialog')&&e.open).map(([,e])=>e):selector==='input,textarea'?keys.map(get):[];},createElement:element,createTextNode:text=>({textContent:text}),addEventListener(){}},
 fetch:async()=>response({authenticated:false})});
 vm.runInContext(readFileSync(new URL('../web/app.js',import.meta.url),'utf8').replace(/^import .*?;\n/gm,'')+'\nglobalThis.app={state,chats,clearPrivate,showTask,saveTask,quickComplete,deleteTask,restoreTask,showUpdates,acknowledge,backgroundRefresh,refreshTasks,mutate,closeTask,render,openTask};',context);
 await tick();const app=context.app;
 Object.assign(app.state,{authenticated:true,online:true,cursor:0,data:{tasks:[task],today:'2026-11-09',week_end:'2026-11-15'}});
 get('sort').value='due';app.showTask(task);get('app').hidden=false;get('login').hidden=true;
 context.fetch=async(path,options)=>{calls.push({path,options});return response(path.startsWith('/api/events')?{events:[],cursor:0,has_more:false}:task);};
 return {...app,context,get,calls};
}

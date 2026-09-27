import './OldGold.css';
import Phase3OperationsModule from './Phase3OperationsModule';

export default function OldGoldModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-old-gold"><Phase3OperationsModule module="Old Gold" ready={ready} onNotice={onNotice} /></section>;
}

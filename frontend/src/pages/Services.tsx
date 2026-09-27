import './Services.css';
import Phase3OperationsModule from './Phase3OperationsModule';

export default function ServicesModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-services"><Phase3OperationsModule module="Services" ready={ready} onNotice={onNotice} /></section>;
}

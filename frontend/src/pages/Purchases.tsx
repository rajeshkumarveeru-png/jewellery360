import './Purchases.css';
import Phase3OperationsModule from './Phase3OperationsModule';

export default function PurchasesModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-purchases"><Phase3OperationsModule module="Purchases" ready={ready} onNotice={onNotice} /></section>;
}

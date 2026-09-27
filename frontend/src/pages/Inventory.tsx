import './Inventory.css';
import Phase3OperationsModule from './Phase3OperationsModule';

export default function InventoryModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  return <section className="menuPage menu-inventory"><Phase3OperationsModule module="Inventory" ready={ready} onNotice={onNotice} /></section>;
}

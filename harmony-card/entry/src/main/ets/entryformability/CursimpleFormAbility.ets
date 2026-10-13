import { FormExtensionAbility, formBindingData, formInfo, formProvider } from '@kit.FormKit';
import { Want } from '@kit.AbilityKit';
import { CardStore } from '../common/CardStore';

export default class CursimpleFormAbility extends FormExtensionAbility {
  onAddForm(want: Want): formBindingData.FormBindingData {
    const id = want.parameters![formInfo.FormParam.IDENTITY_KEY] as string;
    const name = want.parameters![formInfo.FormParam.NAME_KEY] as string;
    const store = new CardStore(this.context); store.addForm(id, name);
    return formBindingData.createFormBindingData(store.data(name));
  }
  onUpdateForm(id: string): void {
    const store = new CardStore(this.context);
    const name = store.read().forms.find((form) => form.id === id)?.name || 'courses';
    formProvider.updateForm(id, formBindingData.createFormBindingData(store.data(name))).catch(() => {});
  }
  onRemoveForm(id: string): void { new CardStore(this.context).removeForm(id); }
  onAcquireFormState(_: Want): formInfo.FormState { return formInfo.FormState.READY; }
}
